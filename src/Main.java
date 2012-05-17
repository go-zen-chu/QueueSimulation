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
	/**到着率*/
	private static final double RAMDA = 0.7;
	/**サービス率*/
	private static final double MU = 0.8;
	/**システム内人数*/
	private static final int K = 50;
	/**統計開始時間*/
	private static final double STAT_START_TIME = 200.0;
	/**統計終了時間*/
	private static final double STAT_END_TIME = 1000.0;
	/**シミュレーション終了時間*/
	private static final double SIMULATION_END_TIME = 5000.0;
	/**サーバー数*/
	private static final int NUMBER_OF_SERVER = 10;
	/**サービス時間固定（M/G/c/K のシミュレーションにするかどうか） false..off, true..on*/
	private static final boolean SERVICE_TIME_FIXED = false;
	/**優先パケット（待ち行列の先頭に一気に進める）を作るかどうか false..off, true..on*/
	private static final boolean PRIORITY_PACKET = false;
	/**優先パケットが発生する確率 0.0 - 1.0 の間で設定*/
	private static final double PRIORITY_PACKET_INCIDENCE_RATE = 0.1;

	private static Random mRandom = new Random(System.currentTimeMillis());
	/**固定されたサービス時間*/
	private static double mFixedServiceTime = -1;
	
	private static ArrayList<Event> mEventList;
	private static ArrayList<Packet> mPacketQueue;
	private static ArrayList<Packet> mServedPackets;
	private static ArrayList<Server> mServerList;
	
	
	public static void main(String[] args) {
		// システムデータ保存用のstring
		String systemDataString = "時刻,イベント内容,システム内人数,"
										+ "待ちパケット数,稼働サーバ数,統計開始判定" + NEWLINE;
		// 初期化
		double previousEventTime = 0, currentEventTime = 0;
		int packetID = 0;
		int systemPacketsNumber = 0;
		boolean isStatisticStarted = false;
		int averagePacketsNum = 0;
		double averageSystemInTime = 0;
		double packetLostRate = 0;
		mEventList = new ArrayList<Event>(100);
		mPacketQueue = new ArrayList<Packet>(K);
		mServedPackets = new ArrayList<Packet>(1000);
		mServerList = new ArrayList<Server>(NUMBER_OF_SERVER);
		for(int i = 0; i < NUMBER_OF_SERVER; i++)
			mServerList.add(new Server(i, Server.MODE_EMPTY));
		
		Event firstArrivalEvent = new Event(Event.TYPE_ARRIVAL, 0);
		mEventList.add(firstArrivalEvent);
		Event startStatisticEvent = new Event(Event.TYPE_START_STATISTIC, STAT_START_TIME);
		mEventList.add(startStatisticEvent);
		Event endStatisticEvent = new Event(Event.TYPE_END_SIMULATION, STAT_END_TIME);
		mEventList.add(endStatisticEvent);
		Event endSimulationEvent = new Event(Event.TYPE_END_SIMULATION, SIMULATION_END_TIME);
		mEventList.add(endSimulationEvent);
		// eventlistを時系列化
		Collections.sort(mEventList, new TimeComparator());
		
		while (true) {
			Event newEvent = mEventList.remove(0);
			// シミュレーションが終わったとき、ループから抜ける
			if(newEvent.mTime > SIMULATION_END_TIME) break;
			// 一つ前のイベントの時間を保存（システム内パケット数を求めるのに用いる）
			previousEventTime = currentEventTime;
			// 時間をイベントの時間に設定する
			currentEventTime = newEvent.mTime;
			switch (newEvent.mType) {
			
			case Event.TYPE_ARRIVAL:
				// 新しく到着したパケットを生成
				Packet newPacket = new Packet(packetID, currentEventTime);
				systemPacketsNumber = getSystemPacketsNumber();
				if(isStatisticStarted){
					// 系内パケット数 * そのパケット数だった時間を追加
					averagePacketsNum += systemPacketsNumber * (currentEventTime - previousEventTime);
					// このパケットを統計の考慮に入れる
					newPacket.measureThisPacket();
				}
				if(systemPacketsNumber >= K){	// システム容量を超えた（呼損）
					// 呼損したというフラグを立てる
					newPacket.loseThisPacket();
					mServedPackets.add(newPacket);
				}else if(!mPacketQueue.isEmpty()){
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
				// 次のイベントを用意
				Event nextArrivalEvent = new Event(Event.TYPE_ARRIVAL, currentEventTime + makeArrivalPoisson());
				mEventList.add(nextArrivalEvent);
				break;
				
			case Event.TYPE_SERVED:
				systemPacketsNumber = getSystemPacketsNumber();
				if(isStatisticStarted){
					// 系内パケット数 * そのパケット数だった時間を追加
					averagePacketsNum += systemPacketsNumber * (currentEventTime - previousEventTime);
				}
				// サービスを終了するサーバを取り出す
				Server dequeuingServer = mServerList.get(newEvent.mServerID);
				dequeuingServer.endServingPacket(currentEventTime);
				if(mPacketQueue.isEmpty()){	// パケットキューが空なので、サーバーが空になる
					dequeuingServer.setEmpty();
				}else{	// パケットキューにパケットがあるので、サーバーに入れる
					dequeuingServer.startServingPacket(mPacketQueue.remove(0), currentEventTime);
				}
				break;
				
			case Event.TYPE_START_STATISTIC:
				isStatisticStarted = true;
				break;
				
			case Event.TYPE_END_SIMULATION:
				systemPacketsNumber = getSystemPacketsNumber();
				averagePacketsNum += systemPacketsNumber * (currentEventTime - previousEventTime);
				isStatisticStarted = false;
				break;
			}
			Collections.sort(mEventList, new TimeComparator());
			int packetQueueSize = mPacketQueue.size();
			systemDataString += newEvent.mTime + "," + newEvent.mType + "," 
									+ systemPacketsNumber + "," + packetQueueSize + "," 
									+ (systemPacketsNumber - packetQueueSize) + ","
									+ String.valueOf(isStatisticStarted) + NEWLINE;
		}
		// 引数はプリミティブ型なので値渡し
		getStatistic(averagePacketsNum, averageSystemInTime, packetLostRate);
		exportAsCsvfile( SAVE_PATH + "systemData.csv",systemDataString);
		// 引数はプリミティブ型なので値渡し
		showResultDialog(averagePacketsNum, averageSystemInTime, packetLostRate);
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
	//====================================================//
	/**実験時間内で到着時間用ポアソン時間を作る*/
	public static double makeArrivalPoisson() {
		return -Math.log( 1 - mRandom.nextDouble()) / RAMDA;
	}
	/**実験時間内でサービス時間用ポアソン時間を作る*/
	public static double makeServicePoisson() {
		if(SERVICE_TIME_FIXED){
			if(mFixedServiceTime < 0){
				mFixedServiceTime = -Math.log( 1 - mRandom.nextDouble()) / MU;
			}
			return mFixedServiceTime;
		}else{
			return -Math.log( 1 - mRandom.nextDouble()) / MU;
		}
	}
	
	//====================================================//
	/**統計結果を出す*/
	public static void getStatistic(int averagePacketsNum,double averageSystemInTime ,double packetLostRate) {
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
				averageSystemInTime += (p.mServedTime - p.mEnqueuedTime);
			}
		}
		exportAsCsvfile( SAVE_PATH + "packetData.csv",packetDataString);
		averagePacketsNum /= (STAT_END_TIME - STAT_START_TIME);
		averageSystemInTime /= (packetCount - lostCount);
		packetLostRate = ((double)lostCount)/ packetCount;
	}
	
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
	
	//====================================================//
	/**結果表示ダイアログ*/
	public static void showResultDialog(int averagePacketsNum, double averageSystemInTime,
													double packetLostRate) {
		JFrame selectDialog = new JFrame("実行の結果");
		selectDialog.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		selectDialog.setSize(400, 400);
		selectDialog.setLayout(new GridLayout(1,1));
		
		String viewStr = "<html> 平均系内パケット数::" + averagePacketsNum + "<br><br>"
							+ "平均システム滞在時間::" + averageSystemInTime + "<br><br>"
							+ "パケット棄却率::" + packetLostRate;
		JLabel nodeLabel = new JLabel(viewStr);
		selectDialog.add(nodeLabel);
		selectDialog.setVisible(true);
	}
	
	//====================================================//
	/**Eventの時間を比較する*/
	public static class TimeComparator implements Comparator<Event>{
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
}
