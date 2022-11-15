package github.umer0586.fragments;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.github.ybq.android.spinkit.SpinKitView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.net.BindException;
import java.net.UnknownHostException;

import github.umer0586.R;
import github.umer0586.sensorserver.SensorWebSocketServer;
import github.umer0586.sensorserver.ServerErrorListener;
import github.umer0586.sensorserver.ServerStartListener;
import github.umer0586.sensorserver.ServerStopListener;
import github.umer0586.service.SensorService;
import github.umer0586.service.ServiceBindHelper;
import github.umer0586.util.UIUtil;


public class ServerFragment extends Fragment
        implements ServiceConnection, ServerStartListener, ServerStopListener, ServerErrorListener {

    private static final String TAG = ServerFragment.class.getSimpleName();

    private SensorService sensorService;

    // Button at center to start/stop server
    private MaterialButton startButton;

    // Address of server (ws://192.168.2.1:8081)
    private TextView serverAddress;


    // card view which holds serverAddress
    private CardView cardView;

    //Ripple animation behind startButton
    private SpinKitView pulseAnimation;

    private ServiceBindHelper serviceBindHelper;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState)
    {
        Log.i(TAG, "onCreateView: ");
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_server, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);
        Log.i(TAG, "onViewCreated: ");

        startButton = view.findViewById(R.id.start_button);
        serverAddress = view.findViewById(R.id.server_address);
        pulseAnimation = view.findViewById(R.id.loading_animation);
        cardView = view.findViewById(R.id.card_view);

        serviceBindHelper = new ServiceBindHelper(
                getContext(),
                this,
                SensorService.class
        );


        hidePulseAnimation();
        hideServerAddress();

        // we will use tag to determine last state of button
        startButton.setOnClickListener(v -> {
            if(v.getTag().equals("stopped"))
                startServer();
            else if(v.getTag().equals("started"))
                stopServer();
        });


    }

    private void showServerAddress(final String address)
    {

            cardView.setVisibility(View.VISIBLE);
            serverAddress.setVisibility(View.VISIBLE);
            serverAddress.setText(address);

            showPulseAnimation();

    }


    private void startServer()
    {
        Log.d(TAG, "startServer() called");

        WifiManager wifiManager = (WifiManager) getContext().getApplicationContext().getSystemService(getContext().WIFI_SERVICE);

        if(!wifiManager.isWifiEnabled())
        {
            Snackbar.make(getView(),"Please enable Wi-Fi",Snackbar.LENGTH_SHORT).show();
            return;
        }

        serviceBindHelper.bindToService();
        Intent intent = new Intent(getContext(), SensorService.class);
        ContextCompat.startForegroundService(getContext(),intent);

    }



    private void stopServer()
    {
        Log.d(TAG, "stopServer() called");


       // getContext().stopService(new Intent(getContext(),SensorService.class));
        getContext().sendBroadcast(new Intent(SensorService.ACTION_STOP_SERVER));

    }

    @Override
    public void onPause()
    {
        super.onPause();
        Log.d(TAG, "onPause() called");

        if(sensorService != null)
        {
            sensorService.setServerStartListener(null);
            sensorService.setServerStopListener(null);
            sensorService.setServerErrorListener(null);
        }

        serviceBindHelper.unBindFromService();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        Log.d(TAG, "onResume() called");

        serviceBindHelper.bindToService();

    }

    @Override
    public void onServerStarted(String IP, int port)
    {
        Log.d(TAG, "onServerStarted() called");
        UIUtil.runOnUiThread(()->{

            showServerAddress("ws://"+IP+":"+port);
            showPulseAnimation();

            startButton.setTag("started");
            startButton.setText("STOP");

            showMessage("Server started");
        });

    }

    @Override
    public void onServerStopped()
    {
        Log.d(TAG, "onServerStopped() called ");
        UIUtil.runOnUiThread(()->{

            hideServerAddress();
            hidePulseAnimation();

            startButton.setTag("stopped");
            startButton.setText("START");

           showMessage("Server Stopped");

        });

    }


    @Override
    public void onError(Exception exception)
    {

        UIUtil.runOnUiThread(()->{

            if(exception instanceof BindException)
                showMessage("Port already in use");

            else if (exception instanceof UnknownHostException)
                showMessage("Unable to obtain IP");

            else
                showMessage("Failed to start server");

            Log.w(TAG, "onServerError() called");

            startButton.setTag("stopped");
            startButton.setText("START");

            hideServerAddress();
            hidePulseAnimation();

        });


    }

    // called through on onServiceConnected()
    public void onServerAlreadyRunning(String IP, int port)
    {
        Log.d(TAG, "onServerAlreadyRunning() called");
        UIUtil.runOnUiThread(()->{
            showServerAddress("ws://"+IP+":"+port);
            Toast.makeText(getContext(),"service running",Toast.LENGTH_SHORT).show();
            startButton.setTag("started");
            startButton.setText("STOP");
        });
    }

    private void showPulseAnimation()
    {
        pulseAnimation.setVisibility(View.VISIBLE);
    }

    private void hidePulseAnimation()
    {
        pulseAnimation.setVisibility(View.INVISIBLE);
    }

    private void hideServerAddress()
    {
        cardView.setVisibility(View.GONE);
        serverAddress.setVisibility(View.GONE);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service)
    {
        serviceBindHelper.setBounded(true);
        Log.d(TAG, "onServiceConnected()");

        SensorService.LocalBinder localBinder = (SensorService.LocalBinder) service;
        sensorService =  localBinder.getService();



        Log.d(TAG, "service instance : " + service);

        if(sensorService != null)
        {

            sensorService.setServerStartListener(this);
            sensorService.setServerStopListener(this);
            sensorService.setServerErrorListener(this);

            SensorWebSocketServer  sensorWebSocketServer = sensorService.getSensorWebSocketServer();

            if(sensorWebSocketServer != null)
                if(sensorWebSocketServer.isRunning())
                    onServerAlreadyRunning(sensorWebSocketServer.getAddress().getHostName(),sensorWebSocketServer.getPort());



        }




    }

    @Override
    public void onServiceDisconnected(ComponentName name)
    {
        Log.d(TAG, "onServiceDisconnected() called with: name = [" + name + "]");
        serviceBindHelper.setBounded(false);
    }


    private void showMessage(String message)
    {
        View view = getView();
        Context context = getContext();
        if(view != null)
          Snackbar.make(view,message,Snackbar.LENGTH_SHORT).show();
        else if( context != null)
           Toast.makeText(context,message,Toast.LENGTH_SHORT).show();
        else
            Log.e(TAG, "showMessage() cannot display message as getView() or getContext() returned null" );
    }

}