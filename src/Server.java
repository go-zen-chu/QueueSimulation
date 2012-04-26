
public class Server {

	public static final int MODE_EMPTY = 0;
	public static final int MODE_SERVING = 1;
	
	public static final int ALL_SERVING = -1;
	
	public int mServerID = -1;
	public int mMode = -1;
	public Packet mServingPacket = null;
	
	public Server(int serverID, int mode) {
		mServerID = serverID;
		mMode = mode;
	}
	
	public void startServingPacket(Packet packet, double startServingTime) {
		// 待ち行列を出た時は、サーバーに入る時間と同時刻
		packet.mDequeuedTime = startServingTime;
		packet.calcWaitedTime();
		packet.mServedServerID = mServerID;
		mServingPacket = packet;
		mMode = MODE_SERVING;
		// イベントの追加
		Main.mEventList.add(
				new Event(Event.TYPE_SERVED,
						packet.mDequeuedTime + Main.makeServicePoisson(),
						mServerID));
	}
	
	public void endServingPacket(double endServedTime) {
		mServingPacket.mServedTime = endServedTime;
		mServingPacket.calcWaitedTime();
		Main.mServedPackets.add(mServingPacket);
	}
	
	public void setEmpty() {
		mMode = MODE_EMPTY;
	}
	
}
