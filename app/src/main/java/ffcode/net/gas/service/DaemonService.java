package ffcode.net.gas.service;

import android.app.LauncherActivity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Log;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import ffcode.net.gas.LaunchActivity;
import ffcode.net.gas.R;
import ffcode.net.gas.utils.Contants;
import ffcode.net.gas.utils.IWifiAutoCloseDelegate;
import ffcode.net.gas.utils.NetUtil;
import ffcode.net.gas.utils.PowerManagerUtil;
import ffcode.net.gas.utils.SystemUtils;
import ffcode.net.gas.utils.WifiAutoCloseDelegate;

/**前台Service，使用startForeground
 * 这个Service尽量要轻，不要占用过多的系统资源，否则
 * 系统在资源紧张时，照样会将其杀死
 *
 * Created by jianddongguo on 2017/7/7.
 * http://blog.csdn.net/andrexpert
 */
public class DaemonService extends Service {
    private static final String TAG = "DaemonService";
    public static final int NOTICE_ID = 100;


    private AMapLocationClient mLocationClient;
    private AMapLocationClientOption mLocationOption;

    private int locationCount;

    /**
     * 处理息屏关掉wifi的delegate类
     */
    private IWifiAutoCloseDelegate mWifiAutoCloseDelegate = new WifiAutoCloseDelegate();

    /**
     * 记录是否需要对息屏关掉wifi的情况进行处理
     */
    private boolean mIsWifiCloseable = false;


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        netThread.start();
        if(Contants.DEBUG)
            Log.d(TAG,"DaemonService---->onCreate被调用，启动前台service");
        //如果API大于18，需要弹出一个可见通知
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2){
            Notification.Builder builder = new Notification.Builder(this);
            builder.setSmallIcon(R.mipmap.ic_launcher);
            builder.setContentTitle("嘉燃调度App");
            builder.setContentText("正在使用中...");
            startForeground(NOTICE_ID,builder.build());
            // 如果觉得常驻通知栏体验不好
            // 可以通过启动CancelNoticeService，将通知移除，oom_adj值不变
            Intent intent = new Intent(this,CancelNoticeService.class);
            startService(intent);
        }else{
            startForeground(NOTICE_ID,new Notification());
        }
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (mWifiAutoCloseDelegate.isUseful(getApplicationContext())) {
            mIsWifiCloseable = true;
            mWifiAutoCloseDelegate.initOnServiceStarted(getApplicationContext());
        }

        startLocation();


        // 如果Service被终止
        // 当资源允许情况下，重启service
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopLocation();
        // 如果Service被杀死，干掉通知
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2){
            NotificationManager mManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            mManager.cancel(NOTICE_ID);
        }
        if(Contants.DEBUG)
            Log.d(TAG,"DaemonService---->onDestroy，前台service被杀死");
        // 重启自己
        Intent intent = new Intent(getApplicationContext(),DaemonService.class);
        startService(intent);
    }

    /**
     * 启动定位
     */
    void startLocation() {
        stopLocation();

        if (null == mLocationClient) {
            mLocationClient = new AMapLocationClient(this.getApplicationContext());
        }

        mLocationOption = new AMapLocationClientOption();
        // 使用连续
        mLocationOption.setOnceLocation(false);
        mLocationOption.setLocationCacheEnable(false);
        // 每10秒定位一次
        mLocationOption.setInterval(10 * 1000);
        // 地址信息
        mLocationOption.setNeedAddress(true);
        mLocationClient.setLocationOption(mLocationOption);
        mLocationClient.setLocationListener(locationListener);
        mLocationClient.startLocation();
    }


    /**
     * 停止定位
     */
    void stopLocation() {
        if (null != mLocationClient) {
            mLocationClient.stopLocation();
        }
    }

    AMapLocationListener locationListener = new AMapLocationListener() {
        @Override
        public void onLocationChanged(AMapLocation aMapLocation) {
            //发送结果的通知
            sendLocationBroadcast(aMapLocation);

            if (!mIsWifiCloseable) {
                return;
            }

            if (aMapLocation.getErrorCode() == AMapLocation.LOCATION_SUCCESS) {
                mWifiAutoCloseDelegate.onLocateSuccess(getApplicationContext(), PowerManagerUtil.getInstance().isScreenOn(getApplicationContext()), NetUtil.getInstance().isMobileAva(getApplicationContext()));
            } else {
                mWifiAutoCloseDelegate.onLocateFail(getApplicationContext() , aMapLocation.getErrorCode() , PowerManagerUtil.getInstance().isScreenOn(getApplicationContext()), NetUtil.getInstance().isWifiCon(getApplicationContext()));
            }

        }

        private void sendLocationBroadcast(AMapLocation aMapLocation) {
            //记录信息并发送广播
            locationCount++;
            long callBackTime = System.currentTimeMillis();
            StringBuffer sb = new StringBuffer();
            sb.append("定位完成 第" + locationCount + "次\n");
            sb.append("回调时间: " + SystemUtils.formatUTC(callBackTime, null) + "\n");
            if (null == aMapLocation) {
                sb.append("定位失败：location is null!!!!!!!");
            } else {
                sb.append(SystemUtils.getLocationStr(aMapLocation));
                Message msg = new Message();
                Bundle data = new Bundle();
                data.putString("lng", String.valueOf(aMapLocation.getLongitude()));
                data.putString("lat", String.valueOf(aMapLocation.getLatitude()));
                data.putString("speed", String.valueOf(aMapLocation.getSpeed()));
                data.putString("course", String.valueOf(aMapLocation.getBearing()));
                msg.setData(data);
                msg.what = 0x1;
                netHandler.sendMessage(msg);
            }

            Intent mIntent = new Intent(LaunchActivity.RECEIVER_ACTION);
            mIntent.putExtra("result", sb.toString());

            //发送广播
            sendBroadcast(mIntent);


        }

    };


    Handler netHandler = null;

    /**
     * 收发网络数据的线程
     */
    Thread netThread = new Thread(){
        @Override
        public void run() {
            Looper.prepare();
            netHandler = new Handler(){
                public void dispatchMessage(Message msg) {
                    Bundle data = msg.getData();
                    switch(msg.what){
                        case 0x1: //发送位置
                            upDatePosition(data);
                            break;

                    }
                }
            };
            Looper.loop();
        }
    };


    public void upDatePosition(Bundle bundle) {
        HttpClient httpClient = new DefaultHttpClient();
        String url = "http://192.168.8.5:8360/index/geo";
        HttpPost httpPost = new HttpPost(url);

        NameValuePair pair1 = new BasicNameValuePair("lat", bundle.get("lat").toString());
        NameValuePair pair2 = new BasicNameValuePair("lng", bundle.get("lng").toString());
        NameValuePair pair3 = new BasicNameValuePair("speed", bundle.get("speed").toString());
        NameValuePair pair4 = new BasicNameValuePair("course", bundle.get("course").toString());
        NameValuePair pair5 = new BasicNameValuePair("id", "jack");
        NameValuePair pair6 = new BasicNameValuePair("type", "user");
        ArrayList<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(pair1);
        pairs.add(pair2);
        pairs.add(pair3);
        pairs.add(pair4);
        pairs.add(pair5);
        pairs.add(pair6);
        try {
            HttpEntity requestEntity = new UrlEncodedFormEntity(pairs);
            httpPost.setEntity(requestEntity);
            HttpResponse response = httpClient.execute(httpPost);

            if (response.getStatusLine().getStatusCode() == 200) {
                HttpEntity entity = response.getEntity();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(entity.getContent()));
                String result = reader.readLine();
                Log.d("HTTP", "POST:" + result);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
