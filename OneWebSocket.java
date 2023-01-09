package cn.platform.thinglinks.monitor.utils;


import cn.platform.thinglinks.monitor.hk.hkSdk.HCNetTools;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@ServerEndpoint(value = "/wstest/{lUserID}")
@Component
public class OneWebSocket {
    /** 记录当前在线网页数量 */
    private static AtomicInteger onlineCount = new AtomicInteger(0);

    //与某个客户端的连接会话，需要通过它来给客户端发送数据
    public Session session;
    /**
     * concurrent包的线程安全Set，用来存放每个客户端对应的CumWebSocket对象。
     */
    private static CopyOnWriteArraySet<OneWebSocket> webSocketSet = new CopyOnWriteArraySet<OneWebSocket>();
    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session , @PathParam("lUserID") String lUserID) {
        this.session=session;
        webSocketSet.add(this);
        onlineCount.addAndGet(1);
        System.out.println("当前已经登录用户句柄S:"+MyBlockingQueue.bpMap.keySet());
        log.info("有新连接加入sessionid：{}，摄像头登录用户的句柄为：{} 当前在线socket（视频路数）数量：{}", session.getId(),lUserID, onlineCount);
        if(MyBlockingQueue.bpMap.containsKey(lUserID)){
            if(null==MyBlockingQueue.findPlayIdByUserId(lUserID))
            {
                System.out.println(String.format("警告:根据登录句柄%s,没有找到用户预览句柄",lUserID));
            }
            BlockingQueue blockingQueue = MyBlockingQueue.bpMap.get(lUserID);
            MyBlockingQueue.SessionToUserIdMap.put(session.getId(),lUserID);
            //这里按照逻辑来说这里绑定后就应该开启一个线层来干这个事情，查询了一下好像websocket就是多线程的直接干吧
            while (null!=session&&session.isOpen()&&null!=blockingQueue) {
                try {
                    byte[] esBytes = (byte[]) blockingQueue.take();
                    if(esBytes.length<1) {
                        System.out.println("取流失败，无内容");
                        continue;
                    }
                    ByteBuffer data = ByteBuffer.wrap(esBytes);
                    session.getBasicRemote().sendBinary(data);
                    log.info("发送成功:{}",data);
                } catch (InterruptedException e) {
                    System.out.println("socket 数据发失败，错误信息为："+e.getMessage());
                    return;
                } catch (IOException e) {
                    System.out.println("socket 数据发失败，错误信息为："+e.getMessage());
                    return;
                }
            }
        }
        else
        {
            System.out.println("当前没有找到用户登录句柄,无法播放:"+lUserID);
        }
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose(final Session session) {
        onlineCount.decrementAndGet(); // 在线数减1
        System.out.println(String.format("socket[%s]断开链接,查找并执行退出预览&登录",session.getId()));
        //执行退出操作
        if(MyBlockingQueue.SessionToUserIdMap.containsKey(session.getId()))
        {
            String userId = MyBlockingQueue.SessionToUserIdMap.get(session.getId());
            if(null!=userId)
            {
                System.out.println(String.format("找到正在登录id[%s]预览的的相关信息，执行停止预览并退出登录操作",userId));
                HCNetTools.logoutPlayView(userId);//执行退出预览操作
            }
        }
        else
        {
            System.out.println(String.format("没有找到该socket相关的登录预览信息，无需操作！"));
        }
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message
     * 客户端发送过来的消息
     */
    @OnMessage
    public void onMessage(final String message, final Session session) {
        log.info("服务端收到客户端[{}]的消息:{}", session.getId(), message);
    }

    @OnError
    public void onError(final Session session, final Throwable error) {
        System.out.println(String.format("socket[%s]发生错误,查找并执行退出预览&登录,错误消息是:"+error.getMessage(),session.getId()));
        //执行退出操作
        if(MyBlockingQueue.SessionToUserIdMap.containsKey(session.getId()))
        {
            String userId = MyBlockingQueue.SessionToUserIdMap.get(session.getId());
            if(null!=userId)
            {
                System.out.println(String.format("找到正在登录id[%s]预览的的相关信息，执行停止预览并退出登录操作",userId));
                HCNetTools.logoutPlayView(userId);//执行退出预览操作
            }
        }
        else
        {
            System.out.println(String.format("没有找到该socket相关的登录预览信息，无需操作！"));
        }
        log.error(error.toString());
    }

    /**
     * 服务端发送消息给客户端
     */
    private void sendMessage(final String message, final Session toSession) {
        try {
            log.info("服务端给客户端[{}]发送消息{}", toSession.getId(), message);
            toSession.getBasicRemote().sendText(message);
        } catch (Exception e) {
            log.error("服务端发送消息给客户端失败：{}", e);
        }
    }

    /**
    * @description 发送数据
    * @methodName sendMessage
    * @author zck
    * @date 2022/11/21 17:41
    * @param map
    * @return void
    **/
    public void sendMessage(Map<String, Object> map) throws IOException{
        for (OneWebSocket websocket : webSocketSet) {
            try {
                websocket.session.getBasicRemote().sendText(JSON.toJSONString(map));
                log.info("向客户端发送数据:"+map);
            }catch (Exception e){
                websocket.session.close();
            }
        }
    }

    public CopyOnWriteArraySet<OneWebSocket> getWebSocketSet() {
        return webSocketSet;
    }

}
