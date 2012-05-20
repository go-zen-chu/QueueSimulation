import java.awt.GridLayout;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Random;

import javax.swing.JFrame;
import javax.swing.JLabel;

public class Main {

	/**ファイルん出力先*/
	public static final String SAVE_PATH = "/Users/masudaakira/Desktop/";
	/**改行コード*/
	public static final String NEWLINE = System.getProperty("line.separator");
	/**到着率．次の到着時間は t = -log(1-p)/RAMDA  としているため、
	 * RAMDAが大きければ、パケットも十分到着する．
	 * そのため、シミュレーション時間を少なくして構わない．（十分なパケットが得られるから）
	 * 大体、RAMDA = 1, MU = 1 で統計時間を10000にすると良い．
	 * もしRAMDA = 15,MU = 1なら統計時間が666でもいい*/
	private static final double RAMDA = 0.99;
	/**サービス率*/
	private static final double MU = 1.0;
	/**システム内人数*/
	private static final int K = 50;
	/**統計開始時間*/
	private static final double STAT_START_TIME = 500.0;
	/**統計終了時間*/
	private static final double STAT_END_TIME = 10500.0;
	/**シミュレーション終了時間*/
	private static final double SIMULATION_END_TIME = 10500.0;
	/**サーバー数*/
	private static final int NUMBER_OF_SERVER = 1;
	/**サービス時間固定（M/G/c/K のシミュレーションにするかどうか） false..off, true..on*/
	private static final boolean SERVICE_TIME_FIXED = false;
	/**優先パケットが発生する確率 0.0 - 1.0 の間で設定.  0のとき、優先パケットは発生しない*/
	private static final double PRIORITY_PACKET_INCIDENCE_RATE = 0.0;

	private static Random mRandom = new Random(System.currentTimeMillis());
	/**固定されたサービス時間(サービス時間固定時に用いる)*/
	private static double mFixedServiceTime = 0;
	
	public static ArrayList<Event> mEventQueue;
	private static ArrayList<Packet> mPacketQueue;
	public static ArrayList<Packet> mServedPackets;
	private static ArrayList<Server> mServerList;
	
	public static double p(double raw, int c) {
		if(c == 1){
			return raw/(1+raw);
		}
		double result = p(raw, c-1);
		return raw * result/(c + raw * result);
	}
	public static int found(int i) {
		if(i == 0)
			return 1;
		if(i == 1)
			return 1;
		if(i == 2)
			return 2;
		return i * found(i-1);
	}
	
	public static void main(String[] args) {
		if(PRIORITY_PACKET_INCIDENCE_RATE > 0 && NUMBER_OF_SERVER == K){
			//システムの容量は全部サーバーなので、待ち行列は存在せず、優先パケットはない
			System.out.print("待ち行列が存在しないので、優先パケットは発生出来ません");
			System.exit(1);
		}
		if(SIMULATION_END_TIME < STAT_END_TIME){
			//シミュレーションよりも統計の方が長いことはありえない
			System.out.print("シミュレーション時間が統計時間よりも短いです");
			System.exit(1);
		}
		
//		double raw = 1.0;
//		int c = 10;
//		for(int i = 0; i<31; i++){
//			System.out.println(p(raw,c));
//			raw += 0.5;
//		}
//		System.exit(0);
		
//		double raw = 1.0;
//		int c = 10;
//		for(int i = 0; i<31; i++){
//			double sum2 =0;
//			for(int j = 0; j < c+1; j++)
//				sum2 += Math.pow(raw, j)/ found(j);
//			double sum = 0;
//			for(int k = 1; k < c+1; k++){
//				sum += Math.pow(raw, k)/ found(k-1);
//			}
//			sum /= sum2;
//			System.out.println(sum);
//			raw += 0.5;
//		}
//		System.exit(0);
		
		
		// システムデータ保存用のstring
		String systemDataString = "時刻,イベント内容,システム内人数,"
										+ "待ちパケット数,稼働サーバ数,統計開始判定" + NEWLINE;
		// 初期化
		double previousEventTime = 0, currentEventTime = 0;
		int packetID = 0;
		int systemPacketsNumber = 0;
		boolean isStatisticStarted = false;
		double averagePacketsNum = 0;
		mEventQueue = new ArrayList<Event>(1000);
		mPacketQueue = new ArrayList<Packet>(K);
		mServedPackets = new ArrayList<Packet>(1000);
		mServerList = new ArrayList<Server>(NUMBER_OF_SERVER);
		for(int i = 0; i < NUMBER_OF_SERVER; i++)
			mServerList.add(new Server(i, Server.MODE_EMPTY));
		
		Event firstArrivalEvent = new Event(
				Event.TYPE_PACKET_ARRIVAL, makeArrivalPoisson());
		mEventQueue.add(firstArrivalEvent);
		Event startStatisticEvent = new Event(
				Event.TYPE_START_STATISTIC, STAT_START_TIME);
		mEventQueue.add(startStatisticEvent);
		Event endStatisticEvent = new Event(
				Event.TYPE_END_SIMULATION, STAT_END_TIME);
		mEventQueue.add(endStatisticEvent);
		Event endSimulationEvent = new Event(
				Event.TYPE_END_SIMULATION, SIMULATION_END_TIME);
		mEventQueue.add(endSimulationEvent);
		// eventlistを時系列化
		Collections.sort(mEventQueue, new TimeComparator());
		
		while (true) {
			Event newEvent = mEventQueue.remove(0);
			// シミュレーションが終わったとき、ループから抜ける
			if(newEvent.mTime > SIMULATION_END_TIME) break;
			// 一つ前のイベントの時間を保存（システム内パケット数を求めるのに用いる）
			previousEventTime = currentEventTime;
			// 時間をイベントの時間に設定する
			currentEventTime = newEvent.mTime;
			systemPacketsNumber = getSystemPacketsNumber();
			switch (newEvent.mType) {
			
			case Event.TYPE_PACKET_ARRIVAL:
				// 新しく到着したパケットを生成
				Packet newPacket = new Packet(packetID, currentEventTime);
				if(isStatisticStarted){
					// 系内パケット数 * そのパケット数だった時間を追加
					averagePacketsNum += systemPacketsNumber
							* (currentEventTime - previousEventTime);
					// このパケットを統計の考慮に入れる
					newPacket.measureThisPacket();
				}
				if(systemPacketsNumber >= K){	// システム容量を超えた（呼損）
					// 呼損したというフラグを立てる
					newPacket.loseThisPacket();
					mServedPackets.add(newPacket);
				}else if(!mPacketQueue.isEmpty()){ // システムは満杯ではないが、待ち行列は空いていない（M/M/c/cのとき、常に空）
					mPacketQueue.add(newPacket);
				}else{
					int emptyServerID = getRandomEmptyServerID();
					if(emptyServerID == Server.ALL_SERVING){ // 待ち行列は空だったがサーバーは満杯
						mPacketQueue.add(newPacket);
					}else{ // 待ち行列もサーバーも空いていた
						// サーバーに処理をさせる
						mServerList.get(emptyServerID).startServingPacket(newPacket, currentEventTime);
					}
				}
				pripareNextArraivalEvent(currentEventTime);
				break;
			
			case Event.TYPE_PRIORITY_PACKET_ARRIVAL:
				// 新しく到着したパケットを生成
				Packet newPriorityPacket = new Packet(packetID, currentEventTime);
				// 統計をとるために、優先パケットのフラグを立てる
				newPriorityPacket.givePriorityToThisPacket();
				if(isStatisticStarted){
					// 系内パケット数 * そのパケット数だった時間を追加
					averagePacketsNum += systemPacketsNumber 
							* (currentEventTime - previousEventTime);
					// このパケットを統計の考慮に入れる
					newPriorityPacket.measureThisPacket();
				}
				if(systemPacketsNumber >= K){	// システム容量を超えた（待ち行列の最後部を呼損させる）
					// 一番最後に待っていたパケットを呼損
					Packet queueLastPacket = mPacketQueue.remove(mPacketQueue.size() - 1);
					queueLastPacket.loseThisPacket();
					// 待ち行列の先頭に優先パケットを入れる
					mPacketQueue.add(0, newPriorityPacket);
					mServedPackets.add(queueLastPacket);
				}else if(!mPacketQueue.isEmpty()){
					// 待ち行列の先頭に優先パケットを入れる
					mPacketQueue.add(0, newPriorityPacket);
				}else{
					int emptyServerID = getRandomEmptyServerID();
					if(emptyServerID == Server.ALL_SERVING){ // 待ち行列は空だったがサーバーは満杯
						mPacketQueue.add(newPriorityPacket);
					}else{ // 待ち行列もサーバーも空いていた
						// サーバーに処理をさせる
						mServerList.get(emptyServerID)
							.startServingPacket(newPriorityPacket, currentEventTime);
					}
				}
				pripareNextArraivalEvent(currentEventTime);
				break;
				
			case Event.TYPE_PACKET_SERVED:
				if(isStatisticStarted){
					// 系内パケット数 * そのパケット数だった時間を追加
					averagePacketsNum += systemPacketsNumber * (currentEventTime - previousEventTime);
				}
				// サービスを終了するサーバを取り出す
				Server serviceFinishingServer = mServerList.get(newEvent.mServerID);
				serviceFinishingServer.endServingPacket(currentEventTime);
				if(mPacketQueue.isEmpty()){	// パケットキューが空なので、サーバーが空になる（M/M/c/cのとき、常に空）
					serviceFinishingServer.setEmpty();
				}else{	// パケットキューにパケットがあるので、サーバーに入れる
					serviceFinishingServer.startServingPacket(mPacketQueue.remove(0), currentEventTime);
				}
				break;
				
			case Event.TYPE_START_STATISTIC:
				isStatisticStarted = true;
				break;
				
			case Event.TYPE_END_SIMULATION:
				averagePacketsNum += systemPacketsNumber * (currentEventTime - previousEventTime);
				isStatisticStarted = false;
				break;
			}
			Collections.sort(mEventQueue, new TimeComparator());
			int packetQueueSize = mPacketQueue.size();
			systemDataString += newEvent.mTime + "," + newEvent.mType + "," 
									+ systemPacketsNumber + "," + packetQueueSize + "," 
									+ (systemPacketsNumber - packetQueueSize) + ","
									+ String.valueOf(isStatisticStarted) + NEWLINE;
		}
		SystemData systemData = new SystemData(averagePacketsNum);
		systemData.setStatistics();
		exportAsCsvfile( SAVE_PATH + RAMDA + "_SystemData.csv",systemDataString);
		showResultDialog(systemData);
		return;
	}
	
	/*====================================================
		システムへの処理
	====================================================*/
	
	/**次の到着イベントを用意する*/
	public static void pripareNextArraivalEvent(double currentEventTime) {
		// 次のイベントを用意
		Event nextArrivalEvent;
		double nextArrivalTime = currentEventTime + makeArrivalPoisson();
		// 優先パケット発生率に応じて生成する
		if(mRandom.nextDouble() < PRIORITY_PACKET_INCIDENCE_RATE){
			nextArrivalEvent = new Event(Event.TYPE_PRIORITY_PACKET_ARRIVAL, nextArrivalTime);
		}else{
			nextArrivalEvent = new Event(Event.TYPE_PACKET_ARRIVAL, nextArrivalTime);
		}
		mEventQueue.add(nextArrivalEvent);
	}

	/**系内パケット数を取得する*/
	public static int getSystemPacketsNumber() {
		int activeServers = 0;
		for(Server s: mServerList){
			if(s.mMode == Server.MODE_SERVING){
				activeServers++;
			}
		}
		return activeServers + mPacketQueue.size();
	}
	
	/**空いているサーバーのうち、ランダムで一つ選ぶ（M/M/c用）
	 * もし空いているサーバーがなければ、Server.ALL_SERVINGを返す*/
	public static int getRandomEmptyServerID() {
		ArrayList<Integer> emptyServerIDs = new ArrayList<Integer>();
		for(int i = 0; i < NUMBER_OF_SERVER; i++){
			if(mServerList.get(i).mMode == Server.MODE_EMPTY){
				emptyServerIDs.add(i);
			}
		}
		if(emptyServerIDs.isEmpty()){	// 空なサーバーがない（サーバーに空きがない）
			return Server.ALL_SERVING;
		}else{
			// 空のサーバー数までで乱数を生成、そこから空いているサーバーのIDを取得
			return emptyServerIDs.get(mRandom.nextInt(emptyServerIDs.size()));
		}
	}
	/*====================================================
		計算
	====================================================*/
	/**実験時間内で到着時間用ポアソン時間を作る*/
	public static double makeArrivalPoisson() {
		double randomNumber = 0;
		// 0と1は弾く
		while(randomNumber == 0 || randomNumber == 1){
			randomNumber = mRandom.nextDouble();
		}
		return -Math.log( 1 - randomNumber) / RAMDA;
	}
	/**実験時間内でサービス時間用ポアソン時間を作る*/
	public static double makeServicePoisson() {
		double randomNumber = 0;
		// 0と1は弾く
		while(randomNumber == 0 || randomNumber == 1){
			randomNumber = mRandom.nextDouble();
		}
		if(SERVICE_TIME_FIXED){
			if(mFixedServiceTime == 0){
				mFixedServiceTime = -Math.log( 1 - randomNumber) / MU;
			}
			return mFixedServiceTime;
		}else{
			return -Math.log( 1 - randomNumber) / MU;
		}
	}
	
	/*====================================================
	 	ファイルの入出力
	 ====================================================*/
	
	/**String型をcsvファイルとして指定の場所に保存する*/
	private static void exportAsCsvfile(String fileName, String data) {
		File file = new File(fileName);
		PrintWriter pw = null;
		try {
			pw = new PrintWriter(new BufferedWriter(new FileWriter(file)));
			pw.print(data);
		} catch (IOException e) {
			e.printStackTrace();
		}finally{
			pw.close();
		}
	}
	
	/*====================================================
		GUI
	====================================================*/
	/**結果表示ダイアログ*/
	public static void showResultDialog(SystemData systemData) {
		JFrame selectDialog = new JFrame("実行の結果");
		selectDialog.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		selectDialog.setLocationRelativeTo(null);
		selectDialog.setSize(400, 400);
		selectDialog.setLayout(new GridLayout(1,1));
		
		String viewStr = "<html> 平均系内パケット数::" + systemData.getAveragePacketsNum() + "<br><br>"
							+ "平均システム滞在時間::" + systemData.getAverageSystemInTime() + "<br><br>"
							+ "パケット棄却率::" + systemData.getPacketLostRate() + "<br><br>"
							+ "固定サービス時間::" + mFixedServiceTime + "<br><br>"
							+ "全パケット数::" + mServedPackets.size() + "<br><br>";
		JLabel nodeLabel = new JLabel(viewStr);
		selectDialog.add(nodeLabel);
		selectDialog.setVisible(true);
	}
	
	/*====================================================
		内部クラス
	====================================================*/
	/**Eventの時間を比較する*/
	private static class TimeComparator implements Comparator<Event>{
		@Override
		public int compare(Event event1, Event event2) {
			if(event1.mTime > event2.mTime){
				return 1;
			}else if(event1.mTime < event2.mTime){
				return -1;
			}else{
				return 0;
			}
		}
	}
	/**参照渡しするためのデータクラス*/
	private static class SystemData{
		private double mAveragePacketsNum = 0;
		private double mAverageSystemInTime = 0.0;
		private double mPacketLostRate = 0.0;
		public SystemData(double averagePacketsNum) {
			mAveragePacketsNum = averagePacketsNum;
		}
		public double getAveragePacketsNum() {
			return mAveragePacketsNum;
		}
		public double getAverageSystemInTime() {
			return mAverageSystemInTime;
		}
		public double getPacketLostRate() {
			return mPacketLostRate;
		}
		/**持っているデータから統計をはじき出す*/
		public void setStatistics() {
			int packetCount = 0;
			int lostCount = 0;
			String packetDataString = "パケットID,呼損したか,到着時刻,サービス開始時刻,"
									+ "サービス終了時刻,待ち時間,サービス時間,サービスしたサーバーID" + NEWLINE;
			for(Packet p: mServedPackets){
				if(p.mIsMeasured){	// 統計に含まれるパケット
					packetCount++;
					if(p.mIsLost) lostCount++;
					packetDataString += p.mPacketID + "," + String.valueOf(p.mIsLost) + ","
							+ p.mEnqueuedTime + "," + p.mDequeuedTime + ","
							+ p.mServedTime + "," + p.mWaitedTime + "," + p.mServingTime + ","
							+ p.mServedServerID + NEWLINE;
					// システム滞在時間の全パケット合計を求める
					mAverageSystemInTime += (p.mServedTime - p.mEnqueuedTime);
				}
			}
			exportAsCsvfile( SAVE_PATH + RAMDA + "_PacketData.csv",packetDataString);
			mAveragePacketsNum /= (STAT_END_TIME - STAT_START_TIME);
			mAverageSystemInTime /= (packetCount - lostCount);
			mPacketLostRate = ((double)lostCount)/ packetCount;
			String vitalDataString = "平均系内パケット数,平均系内滞在時間,呼損率,固定サービス時間" + NEWLINE
					+ mAveragePacketsNum + "," + mAverageSystemInTime + ","
					+ mPacketLostRate + "," + mFixedServiceTime + NEWLINE;
			exportAsCsvfile( SAVE_PATH + RAMDA + "_VitalData.csv", vitalDataString);
		}
	}
}
