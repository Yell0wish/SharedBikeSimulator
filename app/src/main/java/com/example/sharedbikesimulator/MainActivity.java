package com.example.sharedbikesimulator;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.amap.api.maps2d.AMap;
import com.amap.api.maps2d.MapView;
import com.amap.api.maps2d.model.LatLng;
import com.amap.api.maps2d.model.MyLocationStyle;
import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity11";
    private static final int REQUEST_BLUETOOTH_CONNECT_PERMISSION = 1;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static final int REQUEST_ENABLE_BT = 1;


    MapView mMapView = null;
    AMap aMap = null;
    AMapLocationClient mLocationClient = null;
    AMapLocationClientOption mLocationOption = null;

    private final OkHttpClient httpClient = new OkHttpClient();
    private ImageView qrCodeImageView;
    private Button generateButton;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket socket;
    private OutputStream outputStream;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AMapLocationClient.updatePrivacyAgree(this, true);
        AMapLocationClient.updatePrivacyShow(this, true, true);

        // 获取地图控件引用
        mMapView = findViewById(R.id.map);
        // 在activity执行onCreate时执行mMapView.onCreate(savedInstanceState)，创建地图
        mMapView.onCreate(savedInstanceState);

        qrCodeImageView = findViewById(R.id.qrCodeImageView);
        generateButton = findViewById(R.id.generateButton);

        // 请求位置权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_BLUETOOTH_CONNECT_PERMISSION);
            } else {
                setupButtonListener();
            }
        } else {
            setupButtonListener();
        }

        // 初始化AMap对象
        if (aMap == null) {
            aMap = mMapView.getMap();
        }

        // 设置定位蓝点样式
        MyLocationStyle myLocationStyle = new MyLocationStyle();
        myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_FOLLOW);
        aMap.setMyLocationStyle(myLocationStyle);
        aMap.setMyLocationEnabled(true);

        // 初始化定位
        try {
            initLocation();
        } catch (Exception e) {
            Log.d(TAG, "initLocation failed: " + e.getMessage());
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "设备不支持蓝牙", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void setupButtonListener() {
        generateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 检查并请求BLUETOOTH_CONNECT权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT)
                            != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                                REQUEST_BLUETOOTH_CONNECT_PERMISSION);
                    } else {
                        generateQRCodeAndStartServer();
                    }
                } else {
                    generateQRCodeAndStartServer();
                }
            }
        });
    }

    private void generateQRCodeAndStartServer() {
        Log.d(TAG, "获取蓝牙信息");
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            getBluetoothInfo();
        }

        startServer();
    }

    private void getBluetoothInfo() {
        try {
            String deviceName = bluetoothAdapter.getName();
            String macAddress = bluetoothAdapter.getAddress();

            if (deviceName == null) {
                deviceName = "未知设备名称";
            }

            if (macAddress == null) {
                macAddress = "无法获取MAC地址";
            }
            String uuid = "00001101-0000-1000-8000-00805f9b34fb";
            String bluetoothInfo = "DEVICE_NAME:" + deviceName + ",MAC_ADDRESS:AC:76:4C:88:F9:3A" + ",UUID:" + uuid;
            Log.d(TAG, "Bluetooth Info: " + bluetoothInfo);
            generateQRCode(bluetoothInfo);
        } catch (SecurityException e) {
            Log.e(TAG, "缺少BLUETOOTH_CONNECT权限", e);
            Toast.makeText(this, "需要BLUETOOTH_CONNECT权限才能获取蓝牙信息", Toast.LENGTH_SHORT).show();
        }
    }

    private void generateQRCode(String text) {
        BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
        try {
            BitMatrix bitMatrix = barcodeEncoder.encode(text, BarcodeFormat.QR_CODE, 400, 400);
            Bitmap bitmap = barcodeEncoder.createBitmap(bitMatrix);
            qrCodeImageView.setImageBitmap(bitmap);
        } catch (WriterException e) {
            e.printStackTrace();
        }
    }

    private void initLocation() throws Exception {
        // 初始化定位
        mLocationClient = new AMapLocationClient(getApplicationContext());
        // 设置定位回调监听
        mLocationClient.setLocationListener(new AMapLocationListener() {
            @Override
            public void onLocationChanged(AMapLocation aMapLocation) {
                if (aMapLocation != null) {
                    if (aMapLocation.getErrorCode() == 0) {
                        // 获取当前坐标并输出
                        double latitude = aMapLocation.getLatitude();
                        double longitude = aMapLocation.getLongitude();
                        Log.d(TAG, "当前坐标: " + latitude + ", " + longitude);
                        LatLng latLng = new LatLng(latitude, longitude);
                        // 同时调整缩放比例
                        aMap.moveCamera(com.amap.api.maps2d.CameraUpdateFactory.changeLatLng(latLng));
                        aMap.moveCamera(com.amap.api.maps2d.CameraUpdateFactory.zoomTo(20)); // Adjust this value to your desired zoom level
                        // 发送POST请求
                        sendPostRequest(aMapLocation.getLatitude(), aMapLocation.getLongitude());

                        // 发送蓝牙数据
                        sendBluetoothData("Latitude: " + latitude + ", Longitude: " + longitude);
                    } else {
                        // 显示错误信息ErrCode是错误码，errInfo是错误信息，详见错误码表。
                        Log.e(TAG, "定位失败，错误码: " + aMapLocation.getErrorCode() + ", 错误信息: " + aMapLocation.getErrorInfo());
                    }
                }
            }
        });

        // 初始化定位参数
        mLocationOption = new AMapLocationClientOption();
        // 设置定位模式为高精度模式
        mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        // 设置定位间隔
        mLocationOption.setInterval(2000);
        // 设置定位参数
        mLocationClient.setLocationOption(mLocationOption);
        // 启动定位
        mLocationClient.startLocation();
    }

    private void sendPostRequest(double latitude, double longitude) {
        int uuid = 123; // 示例UUID，你可以根据实际情况设置

        String url = "http://192.168.1.13:8081/bike-data";

        // 设置表单数据
        String formData = "uuid=" + uuid + "&getLongitude=" + longitude + "&getLatitude=" + latitude;

        RequestBody body = RequestBody.create(formData, MediaType.get("application/x-www-form-urlencoded; charset=utf-8"));

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "请求失败: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    Log.d(TAG, "请求成功: " + response.body().string());
                } else {
                    Log.e(TAG, "请求失败: " + response.code());
                }
            }
        });
    }

    private void sendBluetoothData(String data) {
        if (socket != null && outputStream != null) {
            try {
                outputStream.write((data + "\n").getBytes());
                Log.d(TAG, "蓝牙数据发送成功: " + data);
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "蓝牙数据发送失败", e);
            }
        } else {
            Log.e(TAG, "未连接到客户端，无法发送蓝牙数据");
        }
    }

    private void startServer() {
        new Thread(() -> {
            BluetoothServerSocket serverSocket = null;
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("BTServerApp", MY_UUID);
                socket = serverSocket.accept();
                outputStream = socket.getOutputStream();

                runOnUiThread(() -> Toast.makeText(this, "客户端已连接", Toast.LENGTH_SHORT).show());

                InputStream inputStream = socket.getInputStream();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                String receivedData;
                while ((receivedData = bufferedReader.readLine()) != null) {
                    String finalReceivedData = receivedData;
                    runOnUiThread(() -> Toast.makeText(this, "接收到客户端数据: " + finalReceivedData, Toast.LENGTH_SHORT).show());
                }
            } catch (IOException e) {
                Log.e(TAG, "服务器启动失败", e);
            } finally {
                if (serverSocket != null) {
                    try {
                        serverSocket.close();
                    } catch (IOException e) {
                        Log.e(TAG, "无法关闭服务器套接字", e);
                    }
                }
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 在activity执行onDestroy时执行mMapView.onDestroy()，销毁地图
        mMapView.onDestroy();
        if (mLocationClient != null) {
            mLocationClient.stopLocation();
            mLocationClient.onDestroy();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 在activity执行onResume时执行mMapView.onResume()，重新绘制加载地图
        mMapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 在activity执行onPause时执行mMapView.onPause()，暂停地图的绘制
        mMapView.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // 在activity执行onSaveInstanceState时执行mMapView.onSaveInstanceState(outState)，保存地图当前的状态
        mMapView.onSaveInstanceState(outState);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_CONNECT_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                generateQRCodeAndStartServer();
            } else {
                Toast.makeText(this, "需要BLUETOOTH_CONNECT权限才能获取蓝牙信息", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
