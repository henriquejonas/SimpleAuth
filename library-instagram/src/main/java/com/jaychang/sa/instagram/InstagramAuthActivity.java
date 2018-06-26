package com.jaychang.sa.instagram;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.jaychang.sa.AuthData;
import com.jaychang.sa.AuthDataHolder;
import com.jaychang.sa.DialogUtils;
import com.jaychang.sa.SimpleAuthActivity;
import com.jaychang.sa.SocialUser;
import com.jaychang.sa.utils.AppUtils;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class InstagramAuthActivity extends SimpleAuthActivity {

  private static final String AUTH_URL = "https://api.instagram.com/oauth/authorize/?client_id=%1$s&redirect_uri=%2$s&response_type=code&scope=%3$s";
  private static final String TOKEN_URL = "https://api.instagram.com/oauth/access_token";
  private static final String PAGE_LINK = "https://www.instagram.com/%1$s/";

  private String clientId;
  private String clientSecret;
  private String redirectUrl;
  private ProgressDialog loadingDialog;

  public static void start(Context context) {
    Intent intent = new Intent(context, InstagramAuthActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(intent);
  }

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    clientId = AppUtils.getMetaDataValue(this, getString(R.string.com_jaychang_sa_instagramClientId));
    clientSecret = AppUtils.getMetaDataValue(this, getString(R.string.com_jaychang_sa_instagramClientSecret));
    redirectUrl = AppUtils.getMetaDataValue(this, getString(R.string.com_jaychang_sa_instagramRedirectUrl));

    loadingDialog = DialogUtils.createLoadingDialog(this);

    String scopes = TextUtils.join("+", getAuthData().getScopes());

    String url = String.format(AUTH_URL, clientId, redirectUrl, scopes);

    WebView webView = new WebView(this);
    webView.loadUrl(url);
    webView.setWebViewClient(new WebViewClient() {

      @Override
      public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        loadingDialog.show();
      }

      @Override
      public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        loadingDialog.dismiss();
      }

      @Override
      public boolean shouldOverrideUrlLoading(WebView view, String url) {
        if (url.startsWith(redirectUrl)) {
          getCode(Uri.parse(url));
          return true;
        }
        return false;
      }

      @Override
      public void onReceivedSslError(WebView view, final SslErrorHandler handler, SslError error) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(InstagramAuthActivity.this);
        builder.setMessage(R.string.ssl_error_certificate);
        builder.setPositiveButton("continue", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            handler.proceed();
          }
        });
        builder.setNegativeButton("cancel", new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            handler.cancel();
          }
        });
        final AlertDialog dialog = builder.create();
        dialog.show();
      }
    });

    setContentView(webView);
  }

  @Override
  protected AuthData getAuthData() {
    return AuthDataHolder.getInstance().instagramAuthData;
  }

  @Override
  public void onBackPressed() {
    handCancel();
  }

  private void getCode(Uri uri) {
    String code = uri.getQueryParameter("code");
    if (code != null) {
      getAccessToken(code);
    } else if (uri.getQueryParameter("error") != null) {
      String errorMsg = uri.getQueryParameter("error_description");
      handleError(new Throwable(errorMsg));
    }
  }

  private void getAccessToken(String code) {
    RequestBody formBody = new FormBody.Builder()
      .add("client_id", clientId)
      .add("client_secret", clientSecret)
      .add("grant_type", "authorization_code")
      .add("redirect_uri", redirectUrl)
      .add("code", code)
      .build();

    Request request = new Request.Builder().post(formBody)
      .url(TOKEN_URL)
      .build();


    loadingDialog.show();

    new OkHttpClient().newCall(request).enqueue(new Callback() {
      @Override
      public void onFailure(Call call, final IOException e) {
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            loadingDialog.dismiss();
            handleError(e);
          }
        });
      }

      @Override
      public void onResponse(Call call, Response response) throws IOException {
        if (!response.isSuccessful()) {
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              loadingDialog.dismiss();
              handleError(new Throwable("Failed to get access token."));
            }
          });
          return;
        }

        String body = response.body().string();

        IgUser igUser = new Gson().fromJson(body, IgUser.class);

        final SocialUser user = new SocialUser();
        user.accessToken = igUser.accessToken;
        user.userId = igUser.user.id;
        user.username = igUser.user.username;
        user.fullName = igUser.user.fullName;
        user.pageLink = String.format(PAGE_LINK, user.username);
        user.profilePictureUrl = igUser.user.profilePicture;

        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            loadingDialog.dismiss();
            handleSuccess(user);
          }
        });
      }
    });
  }

  private static class IgUser {
    @SerializedName("access_token")
    String accessToken;
    @SerializedName("user")
    User user;

    static class User {
      @SerializedName("id")
      String id;
      @SerializedName("username")
      String username;
      @SerializedName("full_name")
      String fullName;
      @SerializedName("profile_picture")
      String profilePicture;
    }
  }
}
