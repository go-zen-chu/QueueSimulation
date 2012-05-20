
public class Event {
	
	public static final int TYPE_PACKET_ARRIVAL = 1;
	public static final int TYPE_PRIORITY_PACKET_ARRIVAL = 2;
	public static final int TYPE_PACKET_SERVED = 3;
	public static final int TYPE_START_STATISTIC = 4;
	public static final int TYPE_END_STATISTIC = 5;
	public static final int TYPE_END_SIMULATION = 6;
	
	public int mType = -1;
	public double mTime = -1;
	public int mServerID = -1;
	
	public Event(int type, double nextEventTime) {
		mType = type;
		mTime = nextEventTime;
	}

	public Event(int type, double nextEventTime, int serverID) {
		mType = type;
		mTime = nextEventTime;
		mServerID = serverID;
	}
}
