package com.example.fubuki.indoor_navi;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import java.util.List;

import com.example.fubuki.indoor_navi.Constant;
import com.example.fubuki.indoor_navi.StepService;

public class MainActivity extends AppCompatActivity implements Handler.Callback {

    private TextView stepData;
    private TextView angleData;

    private SensorManager mSensorManager;

    private Sensor aSensor;
    private Sensor mSensor;
    private float orientationX = 0;
    private float[] accelerometerValues = new float[3];
    private float[] magneticFieldValues = new float[3];
    private float[] orientationValues = new float[3];

    private Sensor stepDetectorSensor;
    private Sensor stepCounterSensor;

    private int mStepDetector = 0;
    private int mStepCounter = 0;

    private static final int STEP_DATA = 1;
    private static final int ANGLE_DATA = 2;

    private long TIME_INTERVAL = 500;
    private TextView text_step;
    private Messenger messenger;
    private Messenger mGetReplyMessenger = new Messenger(new Handler(this));
    private Handler delayHandler;

    private final String TAG = "Main Activity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        text_step = (TextView) findViewById(R.id.text_step);

        stepData = (TextView) findViewById(R.id.stepView);
        angleData = (TextView) findViewById(R.id.angleView);

        //initSensor();
        delayHandler = new Handler(this);
        startServiceForStrategy();
        //更新显示数据的方法
        //calculateOrientation();
    }

    ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                Log.e(TAG,"连接成功");
                messenger = new Messenger(service);
                Message msg = Message.obtain(null, Constant.MSG_FROM_CLIENT);
                msg.replyTo = mGetReplyMessenger;
                messenger.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(sensorListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        /*int suitable = 0;
        List<Sensor> sensorList = mSensorManager.getSensorList(Sensor.TYPE_ALL);

        stepDetectorSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        stepCounterSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);

        for (Sensor sensor : sensorList) {
            if (sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
                suitable += 1;
            } else if (sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
                suitable += 10;
            }
        }
        if (suitable/10>0 && suitable%10>0) {
            mSensorManager.registerListener(sensorListener, stepDetectorSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
            mSensorManager.registerListener(sensorListener, stepCounterSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            stepData.setText("当前设备不支持计步器，请检查是否存在步行检测传感器和计步器传感器");
        }*/
    }



    final SensorEventListener sensorListener = new SensorEventListener() {
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        @Override
        public void onSensorChanged(SensorEvent event) {

            if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
                magneticFieldValues = event.values;
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
                accelerometerValues = event.values;

            if(event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD || event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
                calculateOrientation();
                Message tempMsg = new Message();
                tempMsg.what = ANGLE_DATA;
                handler.sendMessage(tempMsg);
            }

            if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
                if (event.values[0] == 1.0f) {
                    mStepDetector++;
                }
            } else if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
                mStepCounter = (int) event.values[0];
            }

            Message tempMsg = new Message();
            tempMsg.what = STEP_DATA;
            handler.sendMessage(tempMsg);
        }
    };
    private Handler handler = new Handler(){

        public void handleMessage(Message msg){
            switch (msg.what){
                case STEP_DATA:
                    stepData.setText("当前已行走步数："+mStepCounter);
                    break;
                case ANGLE_DATA:
                    angleData.setText("当前角度:"+orientationX);
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case Constant.MSG_FROM_SERVER:
                // 更新界面上的步数
                text_step.setText(msg.getData().getInt("step") + "");
                delayHandler.sendEmptyMessageDelayed(Constant.REQUEST_SERVER, TIME_INTERVAL);
                break;
            case Constant.REQUEST_SERVER:
                try {
                    Message msg1 = Message.obtain(null, Constant.MSG_FROM_CLIENT);
                    msg1.replyTo = mGetReplyMessenger;
                    messenger.send(msg1);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }

                break;
        }
        return false;
    }

    private void initSensor(){
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        aSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mSensorManager.registerListener(sensorListener, aSensor, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(sensorListener, mSensor,SensorManager.SENSOR_DELAY_NORMAL);
    }

    private  void calculateOrientation() {
        float[] values = new float[3];
        float[] R = new float[9];
        SensorManager.getRotationMatrix(R, null, accelerometerValues, magneticFieldValues);
        SensorManager.getOrientation(R, orientationValues);
        if(orientationValues[0] < 0)
            orientationValues[0] = orientationValues[0] + (float)(2*Math.PI);
        // 要经过一次数据格式的转换，转换为度
        values[0] = (float) Math.toDegrees(orientationValues[0]);
        if(Math.abs(values[0] - orientationX) > 3.0){
            orientationX = values[0];
        }
    }

    /**
     * 判断某个服务是否正在运行的方法
     *
     * @param mContext
     * @param serviceName 是包名+服务的类名（例如：net.loonggg.testbackstage.TestService）
     * @return true代表正在运行，false代表服务没有正在运行
     */
    public boolean isServiceWork(Context mContext, String serviceName) {
        boolean isWork = false;
        ActivityManager myAM = (ActivityManager) mContext
                .getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> myList = myAM.getRunningServices(40);
        if (myList.size() <= 0) {
            return false;
        }
        for (int i = 0; i < myList.size(); i++) {
            String mName = myList.get(i).service.getClassName().toString();
            if (mName.equals(serviceName)) {
                isWork = true;
                break;
            }
        }
        return isWork;
    }

    /**
     * 启动service
     *
     * @param flag true-bind和start两种方式一起执行 false-只执行bind方式
     */
    private void setupService(boolean flag) {
        Intent intent = new Intent(MainActivity.this, StepService.class);
        bindService(intent, conn, Context.BIND_AUTO_CREATE);
        if (flag) {
            Log.e(TAG,"开始服务");
            startService(intent);
        }
    }

    private void startServiceForStrategy() {
        if (!isServiceWork(this, StepService.class.getName())) {
            setupService(true);
        } else {
            setupService(false);
        }
    }
}
