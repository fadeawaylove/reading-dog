package com.example.yuejie;
import java.net.*;
public final class HealthCheck {
 public static void main(String[] args)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:3000/health").openConnection();c.setConnectTimeout(3000);c.setReadTimeout(3000);if(c.getResponseCode()!=200)System.exit(1);c.disconnect();}
}
