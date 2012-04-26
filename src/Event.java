
public class Event {
	
	public static final int TYPE_ARRIVAL = 1;
	public static final int TYPE_SERVED = 2;
	public static final int TYPE_START_STATISTIC = 3;
	public static final int TYPE_END_STATISTIC = 4;
	public static final int TYPE_END_SIMULATION = 4;
	
	public int mType = -1;
	public double mTime = -1;
	public int mServerID = -1;
	
	public Event(int type, double time) {
		mType = type;
		mTime = time;
	}

	public Event(int type, double time, int serverID) {
		mType = type;
		mTime = time;
		mServerID = serverID;
	}
}
