package interactive;

/**
 * 布线进度缓存值对象，用于在布线线程和 WebSocket 线程之间传递中间状态。
 * <p>
 * 包含当前未完成连接数和 Specctra Session 文件快照数据。
 * 由 {@link BoardHandling#waitCurrentCache()} 生成，
 * 由 {@code RouterExecutor} 消费并推送给客户端。
 * <p>
 * Immutable value object for routing progress, used to pass intermediate state
 * between the routing thread and the WebSocket thread. Contains the current
 * incomplete net count and a Specctra Session file snapshot.
 *
 * @see BoardHandling#waitCurrentCache()
 */
public class RouterCache {

	/** 当前未完成的网络连接数 / Current number of incomplete net connections */
	public final int inCompletesNum;
	/** Specctra Session 文件快照文本 / Specctra Session file snapshot text */
	public final String data;

	/**
	 * @param inCompletesNum 未完成连接数 / Number of incomplete connections
	 * @param cache          Session 文件文本快照 / Session file text snapshot
	 */
	public RouterCache(int inCompletesNum, String cache) {
		this.inCompletesNum = inCompletesNum;
		this.data = cache;
	}

}
