
public class Packet {
	public int mPacketID = -1;
	public int mServedServerID = -1;
	public double mEnqueuedTime = -1;
	public double mDequeuedTime = -1;
	public double mServedTime = -1;
	public boolean mIsLost = false;
	public boolean mIsMeasured = false;
	public double mWaitedTime = -1;
	public double mServingTime = -1;
	
	public Packet(int packetID, double enqueuedTime) {
		mPacketID = packetID;
		mEnqueuedTime = enqueuedTime;
	}
	
	public void setServerID(Server server) {
		mServedServerID = server.mServerID;
	}
	
	/**パケットが呼損した*/
	public void loseThisPacket() {
		mIsLost = true;
		mDequeuedTime = mEnqueuedTime;
		mServedTime = mEnqueuedTime;
		calcWaitedTime();
		calcServingTime();
	}
	
	public void measureThisPacket() {
		mIsMeasured = true;
	}
	
	public void calcWaitedTime() {
		mWaitedTime = mDequeuedTime - mEnqueuedTime;
	}
	
	public void calcServingTime() {
		mServingTime = mServedTime - mDequeuedTime;
	}
	
}
