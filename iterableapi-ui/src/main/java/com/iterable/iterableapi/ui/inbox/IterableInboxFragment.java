package com.iterable.iterableapi.ui.inbox;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.iterable.iterableapi.IterableActivityMonitor;
import com.iterable.iterableapi.IterableApi;
import com.iterable.iterableapi.IterableInAppDeleteActionType;
import com.iterable.iterableapi.IterableInAppLocation;
import com.iterable.iterableapi.IterableInAppManager;
import com.iterable.iterableapi.IterableInAppMessage;
import com.iterable.iterableapi.IterableInboxSession;
import com.iterable.iterableapi.IterableLogger;
import com.iterable.iterableapi.ui.R;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IterableInboxFragment extends Fragment implements IterableInAppManager.Listener, IterableInboxAdapter.OnListInteractionListener {
    private static final String TAG = "IterableInboxFragment";
    public static final String INBOX_MODE = "inboxMode";

    //Default mode
    private InboxMode inboxMode = InboxMode.POPUP;

    private final SessionManager sessionManager = new SessionManager();
    private @LayoutRes int itemLayoutId = R.layout.fragment_inbox_item;
    private boolean sessionStarted = false;

    public static IterableInboxFragment newInstance() {
        return new IterableInboxFragment();
    }

    public static IterableInboxFragment newInstance(InboxMode inboxMode) {
        IterableInboxFragment inboxFragment = new IterableInboxFragment();
        Bundle bundle = new Bundle();
        bundle.putSerializable(INBOX_MODE, inboxMode);
        inboxFragment.setArguments(bundle);

        return inboxFragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        IterableActivityMonitor.getInstance().addCallback(appStateCallback);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        IterableLogger.printInfo();
        Bundle arguments = getArguments();
        if (arguments != null) {
            if (arguments.get(INBOX_MODE) instanceof InboxMode) {
                inboxMode = (InboxMode) arguments.get(INBOX_MODE);
            }
        }

        RecyclerView view = (RecyclerView) inflater.inflate(R.layout.fragment_inbox_list, container, false);
        view.setLayoutManager(new LinearLayoutManager(getContext()));
        IterableInboxAdapter adapter = new IterableInboxAdapter(IterableApi.getInstance().getInAppManager().getInboxMessages(), itemLayoutId, IterableInboxFragment.this);
        view.setAdapter(adapter);
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new IterableInboxTouchHelper(getContext(), adapter));
        itemTouchHelper.attachToRecyclerView(view);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateList();
        IterableApi.getInstance().getInAppManager().addListener(this);
        startSession();
    }

    @Override
    public void onPause() {
        IterableApi.getInstance().getInAppManager().removeListener(this);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopSession();
        IterableActivityMonitor.getInstance().removeCallback(appStateCallback);
    }

    private final IterableActivityMonitor.AppStateCallback appStateCallback = new IterableActivityMonitor.AppStateCallback() {
        @Override
        public void onSwitchToForeground() {
        }

        @Override
        public void onSwitchToBackground() {
            stopSession();
        }
    };

    private void startSession() {
        if (!sessionStarted) {
            sessionStarted = true;
            sessionManager.onAppDidEnterForeground();
        }
    }

    private void stopSession() {
        if (sessionStarted) {
            sessionStarted = false;
            sessionManager.onAppDidEnterBackground();
        }
    }

    private void updateList() {
        RecyclerView recyclerView = (RecyclerView) getView();
        IterableInboxAdapter adapter = (IterableInboxAdapter) recyclerView.getAdapter();
        adapter.setInboxItems(IterableApi.getInstance().getInAppManager().getInboxMessages());
    }

    @Override
    public void onInboxUpdated() {
        updateList();
    }

    @Override
    public void onListItemTapped(IterableInAppMessage message) {
        IterableApi.getInstance().getInAppManager().setRead(message, true);

        if (inboxMode == InboxMode.ACTIVITY) {
            startActivity(new Intent(getContext(), IterableInboxMessageActivity.class).putExtra(IterableInboxMessageActivity.ARG_MESSAGE_ID, message.getMessageId()));
        } else {
            IterableApi.getInstance().getInAppManager().showMessage(message, IterableInAppLocation.INBOX);
        }
    }

    @Override
    public void onListItemDeleted(IterableInAppMessage message, IterableInAppDeleteActionType source) {
        IterableApi.getInstance().getInAppManager().removeMessage(message, source, IterableInAppLocation.INBOX);
    }

    @Override
    public void onListItemImpressionStarted(IterableInAppMessage message) {
        sessionManager.onMessageImpressionStarted(message);
    }

    @Override
    public void onListItemImpressionEnded(IterableInAppMessage message) {
        sessionManager.onMessageImpressionEnded(message);
    }

    public InboxMode getInboxMode() {
        return inboxMode;
    }

    public void setInboxMode(InboxMode inboxMode) {
        this.inboxMode = inboxMode;
    }

    /**
     * Specify a custom layout ID for inbox items
     *
     * @param itemLayoutId Layout resouce ID for an inbox item
     */
    public void setItemLayoutId(@LayoutRes int itemLayoutId) {
        this.itemLayoutId = itemLayoutId;
    }

    private static class SessionManager {
        IterableInboxSession session = new IterableInboxSession();
        Map<String, ImpressionData> impressions = new HashMap<>();

        private void onAppDidEnterForeground() {
            if (session.sessionStartTime != null) {
                IterableLogger.e(TAG, "Inbox session started twice");
                return;
            }
            session = new IterableInboxSession(
                    new Date(),
                    null,
                    IterableApi.getInstance().getInAppManager().getInboxMessages().size(),
                    IterableApi.getInstance().getInAppManager().getUnreadInboxMessagesCount(),
                    0,
                    0,
                    null);
            IterableApi.getInstance().setInboxSessionId(session.sessionId);
        }

        private void onAppDidEnterBackground() {
            if (session.sessionStartTime == null) {
                IterableLogger.e(TAG, "Inbox Session ended without start");
                return;
            }
            endAllImpressions();
            IterableInboxSession sessionToTrack = new IterableInboxSession(
                    session.sessionStartTime,
                    new Date(),
                    session.startTotalMessageCount,
                    session.startUnreadMessageCount,
                    IterableApi.getInstance().getInAppManager().getInboxMessages().size(),
                    IterableApi.getInstance().getInAppManager().getUnreadInboxMessagesCount(),
                    getImpressionList());
            IterableApi.getInstance().trackInboxSession(sessionToTrack);
            IterableApi.getInstance().clearInboxSessionId();
            session = new IterableInboxSession();
            impressions = new HashMap<>();
        }

        private void onMessageImpressionStarted(IterableInAppMessage message) {
            IterableLogger.printInfo();
            String messageId = message.getMessageId();
            ImpressionData impressionData = impressions.get(messageId);
            if (impressionData == null) {
                impressionData = new ImpressionData(messageId, message.isSilentInboxMessage());
                impressions.put(messageId, impressionData);
            }
            impressionData.startImpression();
        }

        private void onMessageImpressionEnded(IterableInAppMessage message) {
            IterableLogger.printInfo();
            String messageId = message.getMessageId();
            ImpressionData impressionData = impressions.get(messageId);
            if (impressionData == null) {
                IterableLogger.e(TAG, "onMessageImpressionEnded: impressionData not found");
                return;
            }
            if (impressionData.impressionStarted == null) {
                IterableLogger.e(TAG, "onMessageImpressionEnded: impressionStarted is null");
                return;
            }
            impressionData.endImpression();
        }

        private void endAllImpressions() {
            for (ImpressionData impressionData : impressions.values()) {
                impressionData.endImpression();
            }
        }

        private List<IterableInboxSession.Impression> getImpressionList() {
            List<IterableInboxSession.Impression> impressionList = new ArrayList<>();
            for (ImpressionData impressionData : impressions.values()) {
                impressionList.add(new IterableInboxSession.Impression(
                        impressionData.messageId,
                        impressionData.silentInbox,
                        impressionData.displayCount,
                        impressionData.duration
                ));
            }
            return impressionList;
        }
    }

    private static class ImpressionData {
        final String messageId;
        final boolean silentInbox;
        int displayCount = 0;
        float duration = 0.0f;

        Date impressionStarted = null;

        private ImpressionData(String messageId, boolean silentInbox) {
            this.messageId = messageId;
            this.silentInbox = silentInbox;
        }

        private void startImpression() {
            this.impressionStarted = new Date();
        }

        private void endImpression() {
            if (this.impressionStarted != null) {
                this.displayCount += 1;
                this.duration += (float) (new Date().getTime() - this.impressionStarted.getTime()) / 1000;
                this.impressionStarted = null;
            }
        }
    }
}