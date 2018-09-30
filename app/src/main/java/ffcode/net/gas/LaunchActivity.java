package ffcode.net.gas;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.widget.TextView;

import ffcode.net.gas.service.DaemonService;
import ffcode.net.gas.service.PlayerMusicService;

public class LaunchActivity extends CheckPermissionsActivity {

    public static final String RECEIVER_ACTION = "location_in_background";

    private TextView tvResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch);
        tvResult = (TextView) findViewById(R.id.tv_result);

        startDaemonService();
        stopPlayMusicService();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(RECEIVER_ACTION);
        registerReceiver(locationChangeBroadcastReceiver, intentFilter);
    }

    @Override
    protected void onDestroy() {
        if (locationChangeBroadcastReceiver != null)
            unregisterReceiver(locationChangeBroadcastReceiver);

        super.onDestroy();
    }


    private void startDaemonService() {
        Intent intent = new Intent(this, DaemonService.class);
        startService(intent);
    }

    private void stopPlayMusicService() {
        Intent intent = new Intent(this, PlayerMusicService.class);
        stopService(intent);
    }


    private BroadcastReceiver locationChangeBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(RECEIVER_ACTION)) {
                String locationResult = intent.getStringExtra("result");
                if (null != locationResult && !locationResult.trim().equals("")) {
                    tvResult.setText(locationResult);
                }
            }
        }
    };

}
