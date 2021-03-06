package com.iterable.iterableapi.ui.inbox;

import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.iterable.iterableapi.IterableApi;
import com.iterable.iterableapi.IterableInAppLocation;
import com.iterable.iterableapi.IterableInAppMessage;
import com.iterable.iterableapi.ui.R;

import java.util.List;

public class IterableInboxMessageFragment extends Fragment {
    public static final String ARG_MESSAGE_ID = "messageId";
    public static final String LOADED = "loaded";

    private String messageId;
    private WebView webView;
    private IterableInAppMessage message;
    private boolean loaded = false;

    public static IterableInboxMessageFragment newInstance(String messageId) {
        IterableInboxMessageFragment fragment = new IterableInboxMessageFragment();

        Bundle args = new Bundle();
        args.putString(ARG_MESSAGE_ID, messageId);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            messageId = getArguments().getString(ARG_MESSAGE_ID);
        }
        if (savedInstanceState != null) {
            loaded = savedInstanceState.getBoolean(LOADED, false);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(LOADED, true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view  = inflater.inflate(R.layout.iterable_inbox_message_fragment, container, false);
        webView = view.findViewById(R.id.webView);
        loadMessage();
        return view;
    }

    private IterableInAppMessage getMessageById(String messageId) {
        List<IterableInAppMessage> messages = IterableApi.getInstance().getInAppManager().getMessages();
        for (IterableInAppMessage message : messages) {
            if (message.getMessageId().equals(messageId)) {
                return message;
            }
        }
        return null;
    }

    private void loadMessage() {
        message = getMessageById(messageId);
        if (message != null) {
            webView.loadDataWithBaseURL("", message.getContent().html, "text/html", "UTF-8", "");
            webView.setWebViewClient(webViewClient);
            if (!loaded) {
                IterableApi.getInstance().trackInAppOpen(message, IterableInAppLocation.INBOX);
                loaded = true;
            }
            if (getActivity() != null) {
                getActivity().setTitle(message.getInboxMetadata().title);
            }
        }
    }

    private WebViewClient webViewClient = new WebViewClient() {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            IterableApi.getInstance().trackInAppClick(message, url, IterableInAppLocation.INBOX);
            IterableApi.getInstance().getInAppManager().handleInAppClick(message, Uri.parse(url));
            if (getActivity() != null) {
                getActivity().finish();
            }
            return true;
        }
    };
}
