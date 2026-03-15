package com.example.telegramcallnotifier;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TelegramSender {

    private static final String TAG = "TelegramSender";
    private static final String SERVER_URL = "http://37.49.226.139:5000/send";
    private static final String SERVER_API_KEY = "A7f9xP22sKp90ZqLm";

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public TelegramSender(Context context) {
        this.context = context;
    }

    public void sendMessage(String message) {
        CustomExceptionHandler.log(context, "CALL sendMessage() called. msg=" + truncate(message, 500));
        sendToServer("call", message);
    }
    
    public void sendStatusMessage(String message) {
        CustomExceptionHandler.log(context, "REPORT sendStatusMessage() called. msg=" + truncate(message, 500));
        sendToServer("report", message);
    }

    public void sendPing() {
        CustomExceptionHandler.log(context, "PING sendPing() called");
        sendToServer("ping", "alive");
    }

    public void sendToServer(String type, String text) {
        if (text == null || text.isEmpty()) {
            CustomExceptionHandler.log(context, "sendToServer skipped: empty text. type=" + type);
            return;
        }
        final String finalType = (type == null || type.isEmpty()) ? "unknown" : type;
        final String finalText = text;

        CustomExceptionHandler.log(context, "sendToServer start. type=" + finalType + " text=" + truncate(finalText, 500));

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(SERVER_URL);
                CustomExceptionHandler.log(context, "Opening connection to " + SERVER_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(20000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

                String json = "{"
                        + "\"api_key\":\"" + escapeJson(SERVER_API_KEY) + "\","
                        + "\"type\":\"" + escapeJson(finalType) + "\","
                        + "\"text\":\"" + escapeJson(finalText) + "\""
                        + "}";

                CustomExceptionHandler.log(context, "JSON payload ready. len=" + json.length());
                byte[] payload = json.getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(payload.length);

                OutputStream os = conn.getOutputStream();
                os.write(payload);
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                String responseBody = readBody(conn, responseCode >= 200 && responseCode < 300);
                if (responseCode == 200 && finalType.equals("ping")) {
                    CustomExceptionHandler.log(context, "Ping success -> local notification + WakeActivity");
                    showPingNotification();
                    launchWakeActivity();
                }

                CustomExceptionHandler.log(context, "Server response code=" + responseCode);
                if (responseCode >= 200 && responseCode < 300) {
                    Log.d(TAG, "Server OK: " + responseCode);
                } else {
                    Log.e(TAG, "Server failed: " + responseCode);
                }

                CustomExceptionHandler.log(context, "Server response body=" + truncate(responseBody, 2000));
            } catch (Exception e) {
                CustomExceptionHandler.log(context, "sendToServer exception: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                Log.e(TAG, "Error sending to server", e);
                CustomExceptionHandler.logError(context, e);
            } finally {
                if (conn != null) {
                    try {
                        conn.disconnect();
                    } catch (Exception ignored) {
                    }
                }
            }
        });
    }

    private void launchWakeActivity() {
        try {
            android.content.Intent wakeIntent = new android.content.Intent(context, WakeActivity.class);
            wakeIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(wakeIntent);
            CustomExceptionHandler.log(context, "WakeActivity launched from ping success");
        } catch (Exception e) {
            CustomExceptionHandler.log(context, "launchWakeActivity exception: " + e.getMessage());
        }
    }

    private void showPingNotification() {
        try {
            android.app.NotificationManager manager =
                    (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            if (manager == null) return;

            String channelId = "ping_alive_channel";

            android.content.Intent wakeIntent = new android.content.Intent(context, WakeActivity.class);
            wakeIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);

            android.app.PendingIntent fullScreenPendingIntent =
                    android.app.PendingIntent.getActivity(
                            context,
                            5001,
                            wakeIntent,
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
                    );

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                android.app.NotificationChannel channel = new android.app.NotificationChannel(
                        channelId,
                        "Ping Alive",
                        android.app.NotificationManager.IMPORTANCE_HIGH
                );
                channel.enableVibration(true);
                channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
                manager.createNotificationChannel(channel);
            }

            androidx.core.app.NotificationCompat.Builder builder =
                    new androidx.core.app.NotificationCompat.Builder(context, channelId)
                            .setSmallIcon(android.R.drawable.stat_notify_sync)
                            .setContentTitle("Device Alive")
                            .setContentText("Ping OK - server responded")
                            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_CALL)
                            .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
                            .setAutoCancel(true)
                            .setFullScreenIntent(fullScreenPendingIntent, true);

            manager.notify(4001, builder.build());
            CustomExceptionHandler.log(context, "Ping notification shown");
        } catch (Exception e) {
            CustomExceptionHandler.log(context, "showPingNotification exception: " + e.getMessage());
        }
    }

    private static String readBody(HttpURLConnection conn, boolean successStream) {
        InputStream is = null;
        try {
            is = successStream ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) return "";
            BufferedReader in = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line).append('\n');
            }
            return response.toString().trim();
        } catch (Exception e) {
            return "";
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static String escapeJson(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c <= 0x1F) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen);
    }
}
