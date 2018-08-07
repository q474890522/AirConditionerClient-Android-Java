package com.example.chenyiwu.a311bacclient;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


import static java.lang.Thread.sleep;


public class MainActivity extends AppCompatActivity {

    //Socket变量
    private Socket socket;

    //线程池
    private static ExecutorService mThreadPool;

    //输入流对象
    InputStream is;

    //输入流读取对象
    BufferedReader br;

    //输出流对象
    OutputStream out;

    //开关机按钮状态切换
    private boolean ACSwitchFlag;

    //温度范围
    private String Low_Temp, High_Temp;

//    //模式
//    private String Mode;

    //缺省风速  ”低中高”表示
    private String Default_Speed;

    //缺省温度 形式整数表示
    private String Default_Temp;

    //锁，保证同步
    private Lock lock = new ReentrantLock();

    //标志处理9状态包时处理了其他的报文，用于异步
    //private volatile boolean ifHaveBesides_9_ = false;

    //看是否在调度中
    //private boolean ifInDispatching = true;

//    //费用
//    private String Cost;
//
//    //耗能
//    private String Energy;

    List<String> _2_OpenPacket = Collections.synchronizedList(new ArrayList<String>());
    List<String> _4_ShutDownPacket = Collections.synchronizedList(new ArrayList<String>());
    List<String> _6_FunSpeedPacket = Collections.synchronizedList(new ArrayList<String>());
    List<String> _8_UpTempPacket = Collections.synchronizedList(new ArrayList<String>());
    List<String> _8_DownTempPacket = Collections.synchronizedList(new ArrayList<String>());
    List<String> _9_StatePacket = Collections.synchronizedList(new ArrayList<String>());
    List<String> _10_ShutMachinePacket = Collections.synchronizedList(new ArrayList<String>());

    volatile boolean upFlag = false, downFlag = false;

    volatile boolean isOpen_10 = false, isOpen_9 = false;

    //volatile boolean ifSend_3 = false; // 让4不被夺走
    //处理分割*后第一个数组元素为空的情况
    String[] DealWithFirstSplitStr(String[] s) {
        for(int k=0; k<s.length-1; k++){
            s[k] = s[k+1];
        }
        //数组缩容
        s = Arrays.copyOf(s, s.length-1);
        return s;
    }


    public void GetMessageSplit() throws IOException {
        br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        char[] ch = new char[1024];
        int len = br.read(ch);

        String message = new String(ch, 0, len);

        String[] star = message.split("\\*");

        star = DealWithFirstSplitStr(star);

        for(int i = 0; i < star.length; i++) {
            switch(star[i].charAt(0)) {
                case '2':
                    _2_OpenPacket.add(star[i]);
                    break;
                case '4':
                    _4_ShutDownPacket.add(star[i]);
                    break;
                case '6':
                    _6_FunSpeedPacket.add(star[i]);
                    break;
                case '9':
                    _9_StatePacket.add(star[i]);
                    break;
                case '1':
                    _10_ShutMachinePacket.add(star[i]);
                    break;
                case '8':
                    if(upFlag) {
                        _8_UpTempPacket.add(star[i]);
                        upFlag = false;
                    }
                    else if(downFlag) {
                        _8_DownTempPacket.add(star[i]);
                        downFlag = false;
                    }
                    break;
                default:
                    break;
            }
        }
    }

    //发送调风请求
    void SendChangeFunRequest() throws IOException {
        //发风速请求
        out = socket.getOutputStream();
        String strFunSpeed = getFunSpeed();

        if(strFunSpeed.equals("高")){
            out.write(("*5|311B|1").getBytes());
            out.flush();
        }
        else if(strFunSpeed.equals("中")){
            out.write(("*5|311B|3").getBytes());
            out.flush();
        }
        else if(strFunSpeed.equals("低")){
            out.write(("*5|311B|2").getBytes());
            out.flush();
        }
    }

    boolean DealWithChangeFunRequest() throws IOException {

        String receiveMessage = "";

        for(int i=0; i<_6_FunSpeedPacket.size(); i++){
            System.out.println("_6_FunSpeedPacket["+i+"]" + _6_FunSpeedPacket.get(i));
        }

        if(!_6_FunSpeedPacket.isEmpty())
            receiveMessage = _6_FunSpeedPacket.get(0);//每次提出第一个
        //调整风速
        if(receiveMessage.equals("6|1")) {
            if(getFunSpeed().equals("高"))
                setFunSpeed("低");
            else if(getFunSpeed().equals("中"))
                setFunSpeed("高");
            else if(getFunSpeed().equals("低"))
                setFunSpeed("中");
            _6_FunSpeedPacket.remove(0);//处理一次删除最后一个
            return true;
        }
        if(!receiveMessage.equals(""))
            _6_FunSpeedPacket.remove(0);//处理一次删除最后一个
        return false;
    }

    //发送上调温度请求


    boolean SendUpTempRequest() throws IOException {

        out = socket.getOutputStream();
        String sendMessage;
        //发送的消息
        int intHighTemp = Integer.parseInt(getHigh_Temp());
        int intaddAimTempNotSet = Integer.parseInt(addAimTempNotSet());

        if(intaddAimTempNotSet <= intHighTemp){
            upFlag = true;
            sendMessage = "*7|311B|" + addAimTempNotSet();
            out.write((sendMessage).getBytes());
            out.flush();
            return true;
        }
        else {
            System.out.println("超过最高温度限制。");
            return false;
        }
    }

    //发送下调温度请求
    boolean SendDownTempRequest() throws IOException {

        out = socket.getOutputStream();
        String sendMessage;
        //发送的消息
        int intLowTemp = Integer.parseInt(getLow_Temp());
        int intdecAimTempNotSet = Integer.parseInt(decAimTempNotSet());

        if(intdecAimTempNotSet >= intLowTemp) {
            downFlag = true;
            sendMessage = "*7|311B|" + decAimTempNotSet();
            out.write((sendMessage).getBytes());
            out.flush();
            return true;
        }
        else {
            System.out.println("超过最低温度限制。");
            return false;
        }
    }

    //处理加温请求
    boolean DealWithUpRequest() throws IOException {
        String receiveMessage = "";

        for(int i=0; i<_8_UpTempPacket.size(); i++){
            System.out.println("_8_UpTempPacket["+i+"]" + _8_UpTempPacket.get(i));
        }

        if(!_8_UpTempPacket.isEmpty())
            receiveMessage = _8_UpTempPacket.get(0);

        //上调温度
        if (receiveMessage.equals("8|1")) {
            setAimTemp(addAimTempNotSet());
            _8_UpTempPacket.remove(0);
            return true;
        }

        if(!receiveMessage.equals(""))
            _8_UpTempPacket.remove(0);
        return false;

    }

    //处理减温请求
    boolean DealWithDownRequest() throws IOException {
        String receiveMessage = "";


        for(int i=0; i<_8_DownTempPacket.size(); i++){
            System.out.println("_8_DownTempPacket["+i+"]" + _8_DownTempPacket.get(i));
        }
        if(!_8_DownTempPacket.isEmpty())
            receiveMessage = _8_DownTempPacket.get(0);

        //下调温度
        if (receiveMessage.equals("8|1")) {
            setAimTemp(decAimTempNotSet());
            _8_DownTempPacket.remove(0);
            return true;
        }
        if(!receiveMessage.equals(""))
            _8_DownTempPacket.remove(0);
        return false;
    }

    //发送开机请求给服务端
    void SendOpenRequest() throws IOException {

        out = socket.getOutputStream();
        double currentTempDouble = tempStringToDouble(getCurrentTemp());
        //转换double后自动精确到小数点后1位
        String currentTempPrec_1 = tempDoubleToString(currentTempDouble);
        out.write(("*1|311B|" + "18.0").getBytes());
        out.flush();
        setCurrentTemp("18.0");
    }

    //处理开机请求
    boolean DealWithOpenRequest() throws IOException {
        //处理头串尾串，并分割
        //String[][] receiveMessageSplit = GetMessageSplit();
        GetMessageSplit();

        String receiveMessage = "";

        //分配报文
        //DistributePacket(receiveMessageSplit);

        //待处理开机报文
        if(!_2_OpenPacket.isEmpty()) {
            receiveMessage = _2_OpenPacket.get(0);
        }
        /*操作
         *待写
         *
         */
        String[] splitRecvMsg = receiveMessage.split("\\|");
        //判断是否开机成功
        if(splitRecvMsg[1].equals("0")){
            System.out.println("开机失败。");
            ACSwitchFlag = true;
            return false;
        }
        else {
            //设置模式
            setMode(splitRecvMsg[2]);
            //设置缺省温度
            setDefault_Temp(splitRecvMsg[3]);
            //设置缺省风速
            setDefault_Speed(splitRecvMsg[4]);
            //设置最低温
            setLow_Temp(splitRecvMsg[5]);
            //设置最高温
            setHigh_Temp(splitRecvMsg[6]);
            //设置费用
            setCost(splitRecvMsg[7]);
            //设置耗能
            setEnergyConsume(splitRecvMsg[8]);

            //把剩余的写到界面中
            //设置目标温度
            setAimTemp(getDefault_Temp());
            //设置默认风速
            setFunSpeed(getDefault_Speed());
        }
        if(!receiveMessage.equals(""))
            _2_OpenPacket.remove(0);
        return true;
    }

    void SendShutDownRequest() throws IOException {
        //ifSend_3 = true;
        out = socket.getOutputStream();
        out.write(("*3|311B").getBytes());
        out.flush();
    }

    void DealWithShutDownRequest() throws IOException {
        String receiveMessage = "";
        //GetMessageSplit();

        //sleep(200);
        for(int i=0; i<_4_ShutDownPacket.size(); i++){
            System.out.println("_4_ShutDownPacket[0]:" + _4_ShutDownPacket.get(i));
        }

        if(!_4_ShutDownPacket.isEmpty()){
            receiveMessage = _4_ShutDownPacket.get(0);
        }

        //关机操作
        if(receiveMessage.equals("4|1")){
            ACSwitchFlag = true;
            stop_9_10();//关掉线程9——10

            System.out.println("关机成功");
            _4_ShutDownPacket.remove(0);

            //可删
            if(!_9_StatePacket.isEmpty()){
                String whenShutDeal_9_ = _9_StatePacket.get(_9_StatePacket.size() - 1);
                String[] splitRecvMsg = whenShutDeal_9_.split("\\|");

                //设置当前温度
                setCurrentTemp(splitRecvMsg[2]);
                //sleep(100);
                //设置耗能
                setEnergyConsume(splitRecvMsg[3]);
                //sleep(100);
                //设置花费
                setCost(splitRecvMsg[4]);
                //sleep(200);
            }

            //清空ArrayList
            _2_OpenPacket.clear();
            _4_ShutDownPacket.clear();
            _6_FunSpeedPacket.clear();
            _8_DownTempPacket.clear();
            _8_UpTempPacket.clear();
            _9_StatePacket.clear();
            _10_ShutMachinePacket.clear();

            br.close();
            out.close();
            socket.close();

            //ifSend_3 = false;
        }
        else {
            System.out.println(receiveMessage);
            System.out.println("关机失败");
            _4_ShutDownPacket.clear();
        }
    }


    //处理下发状态包
    void DealWith_9_StatePacket(){
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                while(isOpen_9) {
                    try {
                        //sleep(2000);

                        String receiveMessage = "";

                        GetMessageSplit();

                        for(int i=0; i<_9_StatePacket.size(); i++){
                            System.out.println("_9_StatePacket["+i+"]" + _9_StatePacket.get(i));
                        }

                        if(!_9_StatePacket.isEmpty()) {
                            //获取最后一个receiveMessage
                            receiveMessage = _9_StatePacket.get(_9_StatePacket.size() - 1);
                            //取出最后一个，不管是不是上次的都处理它，用它去更新。
                            String[] splitRecvMsg = receiveMessage.split("\\|");

                            //设置当前温度
                            setCurrentTemp(splitRecvMsg[2]);
                            //设置耗能
                            System.out.println("能耗"+splitRecvMsg[3]);
                            setEnergyConsume(splitRecvMsg[3]);
                            //设置花费
                            setCost(splitRecvMsg[4]);
                            _9_StatePacket.clear();
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    void DealWith_10_ShutMachinePacket() {
        mThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                while(isOpen_10) {
                    try {
                        //sleep(500);
                        out = socket.getOutputStream();
                        String currentTemp;
                        String aimTemp;
                        int intCurrentTemp;
                        int intAimTemp;

                        for(int i=0; i<_10_ShutMachinePacket.size(); i++){
                            System.out.println("_10_ShutMachinePacket["+i+"]" + _10_ShutMachinePacket.get(i));
                        }

                        if(!_10_ShutMachinePacket.isEmpty()){
                            System.out.println("已经被踢出队列。回温中");

                            //设置过30s加一度，回温

                            if(getMode().equals("冷")){
                                sleep(10*1000);
                                setCurrentTemp(addCurTempNotSet());
                                currentTemp = getCurrentTemp();
                                aimTemp = getAimTemp();
                                intCurrentTemp = Integer.parseInt(currentTemp);
                                intAimTemp = Integer.parseInt(aimTemp);
                                if(intCurrentTemp - intAimTemp >= 1){
                                    out.write(("*11|311B|" + currentTemp + ".0").getBytes());
                                    out.flush();
                                }
                            }
                            else if(getMode().equals("热")){
                                sleep(10*1000);
                                setCurrentTemp(decCurTempNotSet());
                                currentTemp = getCurrentTemp();
                                aimTemp = getAimTemp();
                                intCurrentTemp = Integer.parseInt(currentTemp);
                                intAimTemp = Integer.parseInt(aimTemp);
                                if(intAimTemp - intCurrentTemp >= 1){
                                    out.write(("*11|311B|" + currentTemp + ".0").getBytes());
                                    out.flush();
                                }
                            }
                            else {
                                System.out.println("没有该模式");
                            }

                            _10_ShutMachinePacket.clear();

                            System.out.println("重新进入调度队列成功");
                        }

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        break;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    public void stop_9_10() {
        isOpen_9 = false;
        isOpen_10 = false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /**
         * 初始化
         */

        //空调开关，风速按钮，上调温度开关，下调温度开关
        //初始化所有按钮
        ImageButton btnACSwitch = findViewById(R.id.button_ACSwitch);
        ImageButton btnFunSwitch = findViewById(R.id.button_FunSwitch);
        ImageButton btnUpSwitch = findViewById(R.id.button_UpSwitch);
        ImageButton btnDownSwitch = findViewById(R.id.button_DownSwitch);

        //初始化空调开关状态转换Flag
        ACSwitchFlag = true;

        //初始化线程池
        mThreadPool = Executors.newCachedThreadPool();

        //创建客户端，连接服务器
        btnACSwitch.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                if(ACSwitchFlag == true) {
                    //利用线程池开启一个线程并执行
                    ACSwitchFlag = false;
                    //ifHaveBesides_9_ = false;
                    mThreadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                //创建Socket对象&指定服务端IP&端口号
                                socket = new Socket("192.168.1.120", 8888);

                                // 判断客户端和服务器是否连接成功
                                System.out.println(socket.isConnected());

                                //发送开机请求给服务端
                                SendOpenRequest();
                                //sleep(200);
                                //处理开机请求
                                boolean ifOpen = DealWithOpenRequest();
                                //需要判断开机是否成功，成功才开始处理9，10
                                if(ifOpen){
                                    //ifHuiWen = false;
                                    isOpen_9 = true;
                                    isOpen_10 = true;
                                    //处理状态包_9_StatePacket和10停机包
                                    DealWith_9_StatePacket();
                                    DealWith_10_ShutMachinePacket();
                                }
                                else {
                                    //有待商榷
                                    br.close();
                                    out.close();
                                    socket.close();
                                }
                            } catch(IOException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
                else {
                    mThreadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            try{
                                //ifHaveBesides_9_ = false;
                                SendShutDownRequest();
                                //sleep(400);
                                DealWithShutDownRequest();
                            } catch(IOException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
            }
        });

        btnFunSwitch.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                mThreadPool.execute(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            //lock.lock();
                            //ifHaveBesides_9_ = false;
                            SendChangeFunRequest();
                            //sleep(200);
                            boolean ifChange = DealWithChangeFunRequest();
                            if (ifChange)
                                System.out.println("调风成功");
                            else
                                System.out.println("调风失败");
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            //lock.unlock();
                        }
                    }
                });
            }
        });


        btnUpSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mThreadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            //lock.lock();
                            //ifHaveBesides_9_ = false;
                            boolean ifSendSuccess = SendUpTempRequest();
                            if (ifSendSuccess) {
                                //加温
                                //sleep(200);
                                boolean ifUp = DealWithUpRequest();
                                if (ifUp)
                                    System.out.println("加温成功");
                                else
                                    System.out.println("加温失败");
                            }
                        } catch(IOException e) {
                            e.printStackTrace();
                        }
                        finally {
                            //lock.unlock();
                        }
                    }
                });
            }
        });

        btnDownSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mThreadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            //lock.lock();
                            //发降温请求
                            //ifHaveBesides_9_ = false;
                            boolean ifSendSuccess = SendDownTempRequest();
                            if (ifSendSuccess) {
                                //sleep(200);
                                boolean ifDown = DealWithDownRequest();
                                if (ifDown)
                                    System.out.println("减温成功");
                                else
                                    System.out.println("减温失败");
                            }
                        } catch(IOException e) {
                            e.printStackTrace();
                        }
                        finally {
                            //lock.unlock();
                        }
                    }
                });
            }
        });
    }
    String getCurrentTemp() {
        TextView tvCurrentTemp = findViewById(R.id.Now_Temperature);
        return tvCurrentTemp.getText().toString();
    }

    String getAimTemp() {
        TextView tvAimTemp = findViewById(R.id.Aim_Temperature);
        return tvAimTemp.getText().toString();
    }

    String getFunSpeed() {
        TextView tvFunSpeed = findViewById(R.id.FunSpeed);
        return tvFunSpeed.getText().toString();
    }

    String getMode() {
        TextView tvMode = findViewById(R.id.Mode);
        return tvMode.getText().toString();
    }

    String getDefault_Temp() {
        return Default_Temp;
    }

    String getDefault_Speed() {
        return Default_Speed;
    }

    String getLow_Temp() {
        return Low_Temp;
    }

    String getHigh_Temp() {
        return High_Temp;
    }

    String getEnergyConsume() {
        TextView tvEnergyConsume = findViewById(R.id.EnergyConsum);
        return tvEnergyConsume.getText().toString();
    }

    String getCost() {
        TextView tvCost = findViewById(R.id.Cost);
        return tvCost.getText().toString();
    }

    void setMode(String str) {
        TextView tvMode = findViewById(R.id.Mode);
        String ChineseMode = "";
        if(str.equals("0"))
            ChineseMode = "冷";
        if(str.equals("1"))
            ChineseMode = "热";
        tvMode.setText(ChineseMode);
    }

    void setFunSpeed(String str) {
        TextView tvFunSpeed = findViewById(R.id.FunSpeed);
        tvFunSpeed.setText(str);
    }

    void setCurrentTemp(String str) {
        double dblCurrentTemp = Double.parseDouble(str);
        int intCurrentTemp = Integer.parseInt(new DecimalFormat("#").format(dblCurrentTemp));
        String strCurrentTemp = String.valueOf(intCurrentTemp);
        TextView tvCurrentTemp = findViewById(R.id.Now_Temperature);
        tvCurrentTemp.setText(strCurrentTemp);
    }

    void setAimTemp(String str) {
        TextView tvAimTemp = findViewById(R.id.Aim_Temperature);
        tvAimTemp.setText(str);
    }

    void setEnergyConsume(String str) {
        TextView tvEnergyConsume = findViewById(R.id.EnergyConsum);
        tvEnergyConsume.setText(str);
    }

    void setCost(String str) {
        TextView tvCost = findViewById(R.id.Cost);
        tvCost.setText(str);
    }

    void setDefault_Temp(String str){
        double dblTemp = tempStringToDouble(str);
        int intTemp = Integer.parseInt(new DecimalFormat("#").format(dblTemp));
        String strTempRounding = String.valueOf(intTemp);
        Default_Temp = strTempRounding;
    }

    void setDefault_Speed(String str) {
        if(str.equals("1"))
            Default_Speed = "低";
        else if(str.equals("2"))
            Default_Speed = "中";
        else if(str.equals("3"))
            Default_Speed = "高";
    }

    void setHigh_Temp(String str) {
        double dblHighTemp = Double.parseDouble(str);
        int intHighTemp = Integer.parseInt(new DecimalFormat("#").format(dblHighTemp));
        String strHighTempRounding = String.valueOf(intHighTemp);
        High_Temp = strHighTempRounding;
    }

    void setLow_Temp(String str) {
        double dblLowTemp = Double.parseDouble(str);
        int intLowTemp = Integer.parseInt(new DecimalFormat("#").format(dblLowTemp));
        String strLowTempRounding = String.valueOf(intLowTemp);
        Low_Temp = strLowTempRounding;
    }

    double tempStringToDouble(String str) {
        return Double.parseDouble(str);
    }

    String tempDoubleToString(Double dbl) {
        return String.valueOf(dbl);
    }

    //加温不设置
    String addAimTempNotSet() {
        //获取目标温度
        String strAimTemperature = getAimTemp();
        //转为int型
        int intAimTemperature = Integer.parseInt(strAimTemperature);
        //温度+1
        int addedIntAimTemperature = intAimTemperature + 1;
        //转为String
        String addedStrAimTemperature = String.valueOf(addedIntAimTemperature);
        return addedStrAimTemperature;
    }

    //减温不设置
    String decAimTempNotSet() {
        String strAimTemperature = getAimTemp();
        //转为int型
        int intAimTemperature = Integer.parseInt(strAimTemperature);
        //温度+1
        int decedIntAimTemperature = intAimTemperature - 1;
        //转为String
        String decedStrAimTemperature = String.valueOf(decedIntAimTemperature);
        return decedStrAimTemperature;
    }

    String addCurTempNotSet() {
        //获取当前温度
        String strCurTemperature = getCurrentTemp();
        //转为int型
        int intCurTemperature = Integer.parseInt(strCurTemperature);
        //温度+1
        int addedIntCurTemperature = intCurTemperature + 1;
        //转为String
        String addedStrCurTemperature = String.valueOf(addedIntCurTemperature);
        return addedStrCurTemperature;
    }

    String decCurTempNotSet() {
        String strCurTemperature = getCurrentTemp();
        //转为int型
        int intCurTemperature = Integer.parseInt(strCurTemperature);
        //温度+1
        int decedIntCurTemperature = intCurTemperature - 1;
        //转为String
        String decedStrCurTemperature = String.valueOf(decedIntCurTemperature);
        return decedStrCurTemperature;
    }
}