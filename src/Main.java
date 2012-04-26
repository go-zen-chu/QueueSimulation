import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Random;

public class Main {

	/**到着率*/
	public static final double RAMDA = 0.9;
	/**サービス率*/
	public static final double MU = 0.8;
	public static final int K = 50;
	public static final double STAT_START_TIME = 200.0;
	public static final double STAT_END_TIME = 1000.0;
	public static final double SIMULATION_END_TIME = 10000.0;
	public static final int NUMBER_OF_SERVER = 1;
	
	public static double mPreviousEventTime = 0;
	public static double mCurrentEventTime = 0;
	public static int mPacketID = 0;
	public static int mSystemPackets = 0;
	public static Random random = new Random(System.currentTimeMillis());
	public static ArrayList<Event> mEventList;
	public static ArrayList<Packet> mPacketQueue;
	public static ArrayList<Packet> mServedPackets;
	public static ArrayList<Server> mServerList;
	public static boolean mIsStatisticStarted = false;
	
	public static int mAveragePacketsNum = 0;
	public static double mAverageSystemInTime = 0;
	public static double mPacketLostRate = 0;
	
	public static void main(String[] args) {
		// 初期化
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
			// シミュレーションが終わったとき
			if(newEvent.mTime > SIMULATION_END_TIME) break;
			// 一つ前のイベントの時間を保存（システム内パケット数を求める）
			mPreviousEventTime = mCurrentEventTime;
			// 時間をイベントの時間に設定する
			mCurrentEventTime = newEvent.mTime;
			
			switch (newEvent.mType) {
			case Event.TYPE_ARRIVAL:
				// 新しいパケットを生成
				Packet newPacket = new Packet(mPacketID, mCurrentEventTime);
				if(mIsStatisticStarted){
					// 系内パケット数 * そのパケット数だった時間を追加
					mAveragePacketsNum += mSystemPackets * (mCurrentEventTime - mPreviousEventTime);
					newPacket.measureThisPacket();
				}
				// パケットを一増やす
				mSystemPackets++;
				// 次のイベントを用意
				Event nextArrivalEvent = new Event(Event.TYPE_ARRIVAL, mCurrentEventTime + makeArrivalPoisson());
				mEventList.add(nextArrivalEvent);
				if(isSystemFull()){	// システム容量を超えた（呼損）
					mSystemPackets--;
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
						mServerList.get(emptyServerID).startServingPacket(newPacket, mCurrentEventTime);
					}
				}
				break;
				
			case Event.TYPE_SERVED:
				if(mIsStatisticStarted){
					// 系内パケット数 * そのパケット数だった時間を追加
					mAveragePacketsNum += mSystemPackets * (mCurrentEventTime - mPreviousEventTime);
				}
				Server dequeuedServer = mServerList.get(newEvent.mServerID);
				dequeuedServer.endServingPacket(mCurrentEventTime);
				if(mPacketQueue.isEmpty()){	// パケットキューが空なので、サーバーが空になる
					dequeuedServer.setEmpty();
				}else{	// パケットキューにパケットがあるので、サーバーに入れる
					dequeuedServer.startServingPacket(mPacketQueue.remove(0), mCurrentEventTime);
				}
				break;
				
			case Event.TYPE_START_STATISTIC:
				mIsStatisticStarted = true;
				break;
				
			case Event.TYPE_END_SIMULATION:
				mIsStatisticStarted = false;
				break;
			}
			Collections.sort(mEventList, new TimeComparator());
		}
		getStatistic();
	}
	
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
			return emptyServerIDs.get(random.nextInt(emptyServerIDs.size()));
		}
	}
	
	/**実験時間内で到着時間用ポアソン時間を作る*/
	public static double makeArrivalPoisson() {
		return -Math.log( 1 - random.nextDouble()) / RAMDA;
	}
	
	/**実験時間内で到着時間用ポアソン時間を作る*/
	public static double makeServicePoisson() {
		return -Math.log( 1 - random.nextDouble()) / MU;
	}
	
	/**システムが満杯かどうか*/
	public static boolean isSystemFull() {
		if( getRandomEmptyServerID() == Server.ALL_SERVING 
				&& mPacketQueue.size() >= K - NUMBER_OF_SERVER)
			// サーバーが満杯で、パケットキューがシステム容量-サーバー数以上
			return true;
		else
			return false;
	}
	
	/**統計結果を出す*/
	public static void getStatistic() {
		int packetCount = 0;
		int lostCount = 0;
		for(Packet p: mServedPackets){
			if(p.mIsMeasured){	// 統計に含まれるパケット
				packetCount++;
				if(p.mIsLost) lostCount++;
				mAverageSystemInTime += (p.mServedTime - p.mEnqueuedTime);
			}
		}
		mAveragePacketsNum /= STAT_END_TIME - STAT_START_TIME;
		mAverageSystemInTime /= packetCount;
		mPacketLostRate = (double)lostCount/ packetCount;
	}
	
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
