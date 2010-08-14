package com.fsck.k9.activity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.text.style.TextAppearanceSpan;
import android.util.Config;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.BaseExpandableListAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ExpandableListView;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SectionIndexer;
import android.widget.TextView;
import android.widget.Toast;

import com.fsck.k9.Account;
import com.fsck.k9.AccountStats;
import com.fsck.k9.FontSizes;
import com.fsck.k9.K9;
import com.fsck.k9.Preferences;
import com.fsck.k9.R;
import com.fsck.k9.SearchSpecification;
import com.fsck.k9.activity.setup.AccountSettings;
import com.fsck.k9.activity.setup.FolderSettings;
import com.fsck.k9.activity.setup.Prefs;
import com.fsck.k9.controller.MessagingController;
import com.fsck.k9.controller.MessagingController.SORT_TYPE;
import com.fsck.k9.controller.MessagingListener;
import com.fsck.k9.grouping.MessageGroup;
import com.fsck.k9.grouping.MessageGrouper;
import com.fsck.k9.grouping.MessageInfo;
import com.fsck.k9.grouping.SingletonMessageGrouper;
import com.fsck.k9.grouping.thread.ThreadMessageGrouper;
import com.fsck.k9.helper.UiThrottler;
import com.fsck.k9.helper.Utility;
import com.fsck.k9.mail.Address;
import com.fsck.k9.mail.Flag;
import com.fsck.k9.mail.Folder;
import com.fsck.k9.mail.Message;
import com.fsck.k9.mail.Message.RecipientType;
import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.store.LocalStore;
import com.fsck.k9.mail.store.LocalStore.LocalFolder;
import com.fsck.k9.mail.store.LocalStore.LocalMessage;


/**
 * MessageList is the primary user interface for the program. This Activity
 * shows a list of messages.
 * From this Activity the user can perform all standard message operations.
 */
public class MessageList
        extends K9Activity
        implements OnClickListener, ExpandableListView.OnChildClickListener, ExpandableListView.OnGroupClickListener
{

    /**
     * Immutable empty {@link Message} array
     */
    private static final Message[] EMPTY_MESSAGE_ARRAY = new Message[0];

    private static final int DIALOG_MARK_ALL_AS_READ = 1;

    private static final int ACTIVITY_CHOOSE_FOLDER_MOVE = 1;
    private static final int ACTIVITY_CHOOSE_FOLDER_COPY = 2;
    private static final int ACTIVITY_CHOOSE_FOLDER_MOVE_BATCH = 3;
    private static final int ACTIVITY_CHOOSE_FOLDER_COPY_BATCH = 4;

    private static final String EXTRA_ACCOUNT = "account";
    private static final String EXTRA_FOLDER  = "folder";
    private static final String EXTRA_QUERY = "query";
    private static final String EXTRA_QUERY_FLAGS = "queryFlags";
    private static final String EXTRA_FORBIDDEN_FLAGS = "forbiddenFlags";
    private static final String EXTRA_INTEGRATE = "integrate";
    private static final String EXTRA_ACCOUNT_UUIDS = "accountUuids";
    private static final String EXTRA_FOLDER_NAMES = "folderNames";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_LIST_POSITION = "listPosition";

    private ExpandableListView mListView;

    private boolean mTouchView = true;

    private MessageListAdapter mAdapter;

    private FolderInfoHolder mCurrentFolder;

    private LayoutInflater mInflater;

    private MessagingController mController;

    private Account mAccount;
    private int mUnreadMessageCount = 0;

    private GestureDetector gestureDetector;
    private View.OnTouchListener gestureListener;
    /**
     * Stores the name of the folder that we want to open as soon as possible
     * after load.
     */
    private String mFolderName;

    /**
     * If we're doing a search, this contains the query string.
     */
    private String mQueryString;
    private Flag[] mQueryFlags = null;
    private Flag[] mForbiddenFlags = null;
    private boolean mIntegrate = false;
    private String[] mAccountUuids = null;
    private String[] mFolderNames = null;
    private String mTitle;

    private MessageListHandler mHandler = new MessageListHandler();

    private SORT_TYPE sortType = SORT_TYPE.SORT_DATE;

    private boolean sortAscending = true;
    private boolean sortDateAscending = false;

    private boolean mStars = true;
    private boolean mCheckboxes = true;
    private int mSelectedCount = 0;

    private View mBatchButtonArea;
    private ImageButton mBatchReadButton;
    private ImageButton mBatchDeleteButton;
    private ImageButton mBatchFlagButton;
    private ImageButton mBatchDoneButton;

    private FontSizes mFontSizes = K9.getFontSizes();

    private Bundle mState = null;

    /**
     * Remember the selection to be consistent between menu display and menu item
     * selection
     */
    private MessageInfoHolder mSelectedMessage = null;

    /**
     * Remember the selection to be consistent between menu display and menu item
     * selection
     */
    private MessageGroup<MessageInfoHolder> mSelectedGroup = null;

    class MessageListHandler
    {
        public void removeMessage(final List<MessageInfoHolder> messages)
        {
            runOnUiThread(new Runnable()
            {
                public void run()
                {
                    for (MessageInfoHolder message : messages)
                    {
                        if (message != null)
                        {
                            if (mFolderName == null || (message.folder != null && message.folder.name.equals(mFolderName)))
                            {
                                if (message.selected && mSelectedCount > 0)
                                {
                                    mSelectedCount--;
                                }
                                mAdapter.messages.remove(message);
                            }
                        }
                    }
                    resetUnreadCountOnThread();

                    mAdapter.notifyDataSetChanged();
                    toggleBatchButtons();
                }
            });
        }

        public void addMessages(final List<MessageInfoHolder> messages)
        {
            final boolean wasEmpty = mAdapter.messages.isEmpty();
            runOnUiThread(new Runnable()
            {
                public void run()
                {
                    for (final MessageInfoHolder message : messages)
                    {
                        if (mFolderName == null || (message.folder != null && message.folder.name.equals(mFolderName)))
                        {
                            int index;
                            synchronized (mAdapter.messages)
                            {
                                index = Collections.binarySearch(mAdapter.messages, message);
                            }

                            if (index < 0)
                            {
                                index = (index * -1) - 1;
                            }

                            mAdapter.messages.add(index, message);
                        }
                    }

                    if (wasEmpty)
                    {
                        mListView.setSelection(0);
                    }
                    resetUnreadCountOnThread();

                    mAdapter.notifyDataSetChanged();
                }
            });
        }

        private void resetUnreadCount()
        {
            runOnUiThread(new Runnable()
            {
                public void run()
                {
                    resetUnreadCountOnThread();
                }
            });
        }

        private void resetUnreadCountOnThread()
        {
            if (mQueryString != null)
            {
                int unreadCount = 0;
                synchronized (mAdapter.messages)
                {
                    for (MessageInfoHolder holder : mAdapter.messages)
                    {
                        unreadCount += holder.read ? 0 : 1;
                    }
                }
                mUnreadMessageCount = unreadCount;
                refreshTitleOnThread();
            }
        }

        private void sortMessages()
        {
            runOnUiThread(new Runnable()
            {
                public void run()
                {
                    synchronized (mAdapter.messages)
                    {
                        Collections.sort(mAdapter.messages);
                    }
                    mAdapter.notifyDataSetChanged();
                }
            });
        }

        public void folderLoading(String folder, boolean loading)
        {
            if (mCurrentFolder != null && mCurrentFolder.name.equals(folder))
            {
                mCurrentFolder.loading = loading;
            }
        }

        private void refreshTitle()
        {
            runOnUiThread(new Runnable()
            {
                public void run()
                {
                    refreshTitleOnThread();
                }
            });
        }

        private void refreshTitleOnThread()
        {
            setWindowTitle();
            setWindowProgress();
        }

        private void setWindowProgress()
        {
            int level = Window.PROGRESS_END;

            if (mCurrentFolder != null && mCurrentFolder.loading && mAdapter.mListener.getFolderTotal() > 0)
            {
                level = (Window.PROGRESS_END / mAdapter.mListener.getFolderTotal()) * (mAdapter.mListener.getFolderCompleted()) ;
                if (level > Window.PROGRESS_END)
                {
                    level = Window.PROGRESS_END;
                }
            }

            getWindow().setFeatureInt(Window.FEATURE_PROGRESS, level);
        }

        private void setWindowTitle()
        {
            String displayName;

            if (mFolderName != null)
            {
                displayName  = mFolderName;

                if (K9.INBOX.equalsIgnoreCase(displayName))
                {
                    displayName = getString(R.string.special_mailbox_name_inbox);
                }

                String dispString = mAdapter.mListener.formatHeader(MessageList.this, getString(R.string.message_list_title, mAccount.getDescription(), displayName), mUnreadMessageCount, getTimeFormat());
                setTitle(dispString);
            }
            else if (mQueryString != null)
            {
                if (mTitle != null)
                {
                    String dispString = mAdapter.mListener.formatHeader(MessageList.this, mTitle, mUnreadMessageCount, getTimeFormat());
                    setTitle(dispString);
                }
                else
                {
                    setTitle(getString(R.string.search_results) + ": "+ mQueryString);
                }
            }
        }

        public void progress(final boolean progress)
        {
            runOnUiThread(new Runnable()
            {
                public void run()
                {
                    showProgressIndicator(progress);
                }
            });
        }
    }

    public static void actionHandleFolder(Context context, Account account, String folder)
    {
        Intent intent = actionHandleFolderIntent(context,account,folder);
        context.startActivity(intent);
    }

    public static Intent actionHandleFolderIntent(Context context, Account account, String folder)
    {
        Intent intent = new Intent(context, MessageList.class);
        intent.putExtra(EXTRA_ACCOUNT, account.getUuid());

        if (folder != null)
        {
            intent.putExtra(EXTRA_FOLDER, folder);
        }
        return intent;
    }

    public static void actionHandle(Context context, String title, String queryString, boolean integrate, Flag[] flags, Flag[] forbiddenFlags)
    {
        Intent intent = new Intent(context, MessageList.class);
        intent.putExtra(EXTRA_QUERY, queryString);
        if (flags != null)
        {
            intent.putExtra(EXTRA_QUERY_FLAGS, Utility.combine(flags, ','));
        }
        if (forbiddenFlags != null)
        {
            intent.putExtra(EXTRA_FORBIDDEN_FLAGS, Utility.combine(forbiddenFlags, ','));
        }
        intent.putExtra(EXTRA_INTEGRATE, integrate);
        intent.putExtra(EXTRA_TITLE, title);
        context.startActivity(intent);
    }

    public static void actionHandle(Context context, String title, SearchSpecification searchSpecification)
    {
        Intent intent = new Intent(context, MessageList.class);
        intent.putExtra(EXTRA_QUERY, searchSpecification.getQuery());
        if (searchSpecification.getRequiredFlags() != null)
        {
            intent.putExtra(EXTRA_QUERY_FLAGS, Utility.combine(searchSpecification.getRequiredFlags(), ','));
        }
        if (searchSpecification.getForbiddenFlags() != null)
        {
            intent.putExtra(EXTRA_FORBIDDEN_FLAGS, Utility.combine(searchSpecification.getForbiddenFlags(), ','));
        }
        intent.putExtra(EXTRA_INTEGRATE, searchSpecification.isIntegrate());
        intent.putExtra(EXTRA_ACCOUNT_UUIDS, searchSpecification.getAccountUuids());
        intent.putExtra(EXTRA_FOLDER_NAMES, searchSpecification.getFolderNames());
        intent.putExtra(EXTRA_TITLE, title);
        context.startActivity(intent);
    }

    @Override
    public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id)
    {
        // FIXME Android 2.1 dosen't invoke this method for expanded groups: can't prevent collapsing
//        if (mAdapter.groupLessMode)
//        {
//            // ignore collapse attempt in groupless mode
//            return true;
//        }

        if (mCurrentFolder != null && ((groupPosition + 1) == mAdapter.getGroupCount()))
        {
            mController.loadMoreMessages(mAccount, mFolderName, mAdapter.mListener);
            return true;
        }
        return false;
    }

    @Override
    public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
            int childPosition, long id)
    {
        final MessageInfoHolder message = mAdapter.getChild(groupPosition, childPosition);
        if (mSelectedCount > 0)
        {
            // In multiselect mode make sure that clicking on the item results
            // in toggling the 'selected' checkbox.
            setSelected(Collections.singletonList(message), !message.selected);
            return true;
        }
        else
        {
            onOpenMessage(message);
            return true;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        mInflater = getLayoutInflater();
        initializeLayout();
        onNewIntent(getIntent());
    }

    @Override
    public void onNewIntent(Intent intent)
    {
        setIntent(intent); // onNewIntent doesn't autoset our "internal" intent

        // Only set "touchable" when we're first starting up the activity.
        // Otherwise we get force closes when the user toggles it midstream.
        mTouchView = K9.messageListTouchable();

        String accountUuid = intent.getStringExtra(EXTRA_ACCOUNT);
        mAccount = Preferences.getPreferences(this).getAccount(accountUuid);
        mFolderName = intent.getStringExtra(EXTRA_FOLDER);
        mQueryString = intent.getStringExtra(EXTRA_QUERY);

        String queryFlags = intent.getStringExtra(EXTRA_QUERY_FLAGS);
        if (queryFlags != null)
        {
            String[] flagStrings = queryFlags.split(",");
            mQueryFlags = new Flag[flagStrings.length];
            for (int i = 0; i < flagStrings.length; i++)
            {
                mQueryFlags[i] = Flag.valueOf(flagStrings[i]);
            }
        }
        String forbiddenFlags = intent.getStringExtra(EXTRA_FORBIDDEN_FLAGS);
        if (forbiddenFlags != null)
        {
            String[] flagStrings = forbiddenFlags.split(",");
            mForbiddenFlags = new Flag[flagStrings.length];
            for (int i = 0; i < flagStrings.length; i++)
            {
                mForbiddenFlags[i] = Flag.valueOf(flagStrings[i]);
            }
        }
        mIntegrate = intent.getBooleanExtra(EXTRA_INTEGRATE, false);
        mAccountUuids = intent.getStringArrayExtra(EXTRA_ACCOUNT_UUIDS);
        mFolderNames = intent.getStringArrayExtra(EXTRA_FOLDER_NAMES);
        mTitle = intent.getStringExtra(EXTRA_TITLE);

        // Take the initial folder into account only if we are *not* restoring
        // the activity already.
        if (mFolderName == null && mQueryString == null)
        {
            mFolderName = mAccount.getAutoExpandFolderName();
        }

        mAdapter = new MessageListAdapter();
        final Object previousData = getLastNonConfigurationInstance();

        if (previousData != null)
        {
            //noinspection unchecked
            mAdapter.messages.addAll((List<MessageInfoHolder>) previousData);
        }

        if (mFolderName != null)
        {
            mCurrentFolder = mAdapter.getFolder(mFolderName, mAccount);
        }

        mController = MessagingController.getInstance(getApplication());
        mListView.setAdapter(mAdapter);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        mController.removeListener(mAdapter.mListener);

        // prevent any throttled UI processing (we're stopping!)
        // (don't set it to null since it needed if a processing is
        // occuring)
        mAdapter.mThrottler.getScheduledExecutorService().shutdown();

        saveListState();
    }

    public void saveListState()
    {
        mState = new Bundle();
        mState.putInt(EXTRA_LIST_POSITION, mListView.getSelectedItemPosition());
    }

    public void restoreListState()
    {
        if (mState == null)
        {
            return;
        }

        int pos = mState.getInt(EXTRA_LIST_POSITION, AdapterView.INVALID_POSITION);

        if (pos >= mListView.getCount())
        {
            pos = mListView.getCount() - 1;
        }

        if (pos == AdapterView.INVALID_POSITION)
        {
            mListView.setSelected(false);
        }
        else
        {
            mListView.setSelection(pos);
        }
    }

    /**
     * On resume we refresh messages for the folder that is currently open.
     * This guarantees that things like unread message count and read status
     * are updated.
     */
    @Override
    public void onResume()
    {
        super.onResume();

        mStars = K9.messageListStars();
        mCheckboxes = K9.messageListCheckboxes();

        sortType = mController.getSortType();
        sortAscending = mController.isSortAscending(sortType);
        sortDateAscending = mController.isSortAscending(SORT_TYPE.SORT_DATE);

        if (mAdapter.mThrottler.getScheduledExecutorService() == null
                || mAdapter.mThrottler.getScheduledExecutorService().isShutdown())
        {
            mAdapter.mThrottler.setScheduledExecutorService(Executors.newScheduledThreadPool(1));
        }

        mController.addListener(mAdapter.mListener);
        mAdapter.messages.clear();
        mAdapter.notifyDataSetChanged();

        if (mFolderName != null)
        {
            mController.listLocalMessages(mAccount, mFolderName,  mAdapter.mListener);
            mController.notifyAccountCancel(this, mAccount);

            MessagingController.getInstance(getApplication()).notifyAccountCancel(this, mAccount);

            mController.getFolderUnreadMessageCount(mAccount, mFolderName, mAdapter.mListener);
        }
        else if (mQueryString != null)
        {
            mController.searchLocalMessages(mAccountUuids, mFolderNames, null, mQueryString, mIntegrate, mQueryFlags, mForbiddenFlags, mAdapter.mListener);
        }

        mHandler.refreshTitle();

        restoreListState();
    }

    private void initializeLayout()
    {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        requestWindowFeature(Window.FEATURE_PROGRESS);
        setContentView(R.layout.message_list);

        mListView = (ExpandableListView) findViewById(R.id.message_list);
        mListView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_INSET);
        mListView.setLongClickable(true);
        mListView.setFastScrollEnabled(true);
        mListView.setScrollingCacheEnabled(true);
        mListView.setOnChildClickListener(this);
        mListView.setOnGroupClickListener(this);

        registerForContextMenu(mListView);

        mBatchButtonArea = findViewById(R.id.batch_button_area);
        mBatchReadButton = (ImageButton) findViewById(R.id.batch_read_button);
        mBatchReadButton.setOnClickListener(this);
        mBatchDeleteButton = (ImageButton) findViewById(R.id.batch_delete_button);
        mBatchDeleteButton.setOnClickListener(this);
        mBatchFlagButton = (ImageButton) findViewById(R.id.batch_flag_button);
        mBatchFlagButton.setOnClickListener(this);
        mBatchDoneButton = (ImageButton) findViewById(R.id.batch_done_button);

        mBatchDoneButton.setOnClickListener(this);

        // Gesture detection
        gestureDetector = new GestureDetector(new MyGestureDetector());
        gestureListener = new View.OnTouchListener()
        {
            public boolean onTouch(View v, MotionEvent event)
            {
                if (gestureDetector.onTouchEvent(event))
                {
                    return true;
                }
                return false;
            }
        };

        mListView.setOnTouchListener(gestureListener);
    }

    @Override
    public Object onRetainNonConfigurationInstance()
    {
        return mAdapter.messages;
    }

    @Override
    public void onBackPressed()
    {
        // This will be called either automatically for you on 2.0
        // or later, or by the code above on earlier versions of the
        // platform.
        if (K9.manageBack())
        {
            if (mQueryString == null)
            {
                onShowFolderList();
            }
            else
            {
                onAccounts();
            }
        }
        else
        {
            finish();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (
            // XXX TODO - when we go to android 2.0, uncomment this
            // android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.ECLAIR &&
            keyCode == KeyEvent.KEYCODE_BACK
            && event.getRepeatCount() == 0
        )
        {
            // Take care of calling this method on earlier versions of
            // the platform where it doesn't exist.
            onBackPressed();
            return true;
        }

        // Shortcuts that work no matter what is selected
        switch (keyCode)
        {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            {
                if (mBatchButtonArea.hasFocus())
                {
                    return false;
                }
                else
                {
                    return true;
                }
            }
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            {
                if (mBatchButtonArea.hasFocus())
                {
                    return false;
                }
                else
                {
                    return true;
                }
            }
            case KeyEvent.KEYCODE_C:
            {
                onCompose();
                return true;
            }
            case KeyEvent.KEYCODE_Q:
            {
                onShowFolderList();
                return true;
            }
            case KeyEvent.KEYCODE_O:
            {
                onCycleSort();
                return true;
            }
            case KeyEvent.KEYCODE_I:
            {
                onToggleSortAscending();
                return true;
            }
            case KeyEvent.KEYCODE_H:
            {
                Toast toast = Toast.makeText(this, R.string.message_list_help_key, Toast.LENGTH_LONG);
                toast.show();
                return true;
            }
        }

        boolean result;
        int position = mListView.getSelectedItemPosition();
        try
        {
            if (position >= 0)
            {
                final Object item = mListView.getItemAtPosition(position);
                if (!(item instanceof MessageInfoHolder))
                {
                    return false;
                }
                MessageInfoHolder message = (MessageInfoHolder) item;

                if (message != null)
                {
                    switch (keyCode)
                    {
                        case KeyEvent.KEYCODE_DEL:
                        {
                            onDelete(Collections.singletonList(message));
                            return true;
                        }
                        case KeyEvent.KEYCODE_S:
                        {
                            setSelected(Collections.singletonList(message), !message.selected);
                            return true;
                        }
                        case KeyEvent.KEYCODE_D:
                        {
                            onDelete(Collections.singletonList(message));
                            return true;
                        }
                        case KeyEvent.KEYCODE_F:
                        {
                            onForward(message);
                            return true;
                        }
                        case KeyEvent.KEYCODE_A:
                        {
                            onReplyAll(message);
                            return true;
                        }
                        case KeyEvent.KEYCODE_R:
                        {
                            onReply(message);
                            return true;
                        }
                        case KeyEvent.KEYCODE_G:
                        {
                            setFlagStatus(Collections.singletonList(message), !message.flagged);
                            return true;
                        }
                        case KeyEvent.KEYCODE_M:
                        {
                            onMove(message);
                            return true;
                        }
                        case KeyEvent.KEYCODE_V:
                        {
                            onArchive(message);
                            return true;
                        }
                        case KeyEvent.KEYCODE_Y:
                        {
                            onCopy(message);
                            return true;
                        }
                        case KeyEvent.KEYCODE_Z:
                        {
                            setReadStatus(Collections.singletonList(message), !message.read);
                            return true;
                        }
                    }
                }
            }
        }
        finally
        {
            result = super.onKeyDown(keyCode, event);
        }

        return result;
    }

    private void onOpenMessage(MessageInfoHolder message)
    {
        if (message.folder.name.equals(message.message.getFolder().getAccount().getDraftsFolderName()))
        {
            MessageCompose.actionEditDraft(this, message.message.getFolder().getAccount(), message.message);
        }
        else
        {
            // Need to get the list before the sort starts
            ArrayList<MessageReference> messageRefs = new ArrayList<MessageReference>();

            synchronized (mAdapter.messages)
            {
                for (MessageInfoHolder holder : mAdapter.messages)
                {
                    MessageReference ref = holder.message.makeMessageReference();
                    messageRefs.add(ref);
                }
            }
            MessageReference ref = message.message.makeMessageReference();
            Log.i(K9.LOG_TAG, "MessageList sending message " + ref);

            MessageView.actionView(this, ref, messageRefs);
        }

        /*
         * We set read=true here for UI performance reasons. The actual value
         * will get picked up on the refresh when the Activity is resumed but
         * that may take a second or so and we don't want this to show and
         * then go away. I've gone back and forth on this, and this gives a
         * better UI experience, so I am putting it back in.
         */
        if (!message.read)
        {
            message.read = true;
        }
    }

    private void onAccounts()
    {
        Accounts.listAccounts(this);
        finish();
    }

    private void onShowFolderList()
    {
        FolderList.actionHandleAccount(this, mAccount);
        finish();
    }

    private void onCompose()
    {
        if (mQueryString != null)
        {
            /*
             * If we have a query string, we don't have an account to let
             * compose start the default action.
             */
            MessageCompose.actionCompose(this, null);
        }
        else
        {
            MessageCompose.actionCompose(this, mAccount);
        }
    }

    private void onEditPrefs()
    {
        Prefs.actionPrefs(this);
    }

    private void onEditAccount()
    {
        AccountSettings.actionSettings(this, mAccount);
    }

    private void changeSort(SORT_TYPE newSortType)
    {
        if (sortType == newSortType)
        {
            onToggleSortAscending();
        }
        else
        {
            sortType = newSortType;
            mController.setSortType(sortType);
            sortAscending = mController.isSortAscending(sortType);
            sortDateAscending = mController.isSortAscending(SORT_TYPE.SORT_DATE);
            reSort();
        }
    }

    private void reSort()
    {
        int toastString = sortType.getToast(sortAscending);

        Toast toast = Toast.makeText(this, toastString, Toast.LENGTH_SHORT);
        toast.show();

        mHandler.sortMessages();
    }

    private void onCycleSort()
    {
        SORT_TYPE[] sorts = SORT_TYPE.values();
        int curIndex = 0;

        for (int i = 0; i < sorts.length; i++)
        {
            if (sorts[i] == sortType)
            {
                curIndex = i;
                break;
            }
        }

        curIndex++;

        if (curIndex == sorts.length)
        {
            curIndex = 0;
        }

        changeSort(sorts[curIndex]);
    }

    private void onToggleSortAscending()
    {
        mController.setSortAscending(sortType, !sortAscending);

        sortAscending = mController.isSortAscending(sortType);
        sortDateAscending = mController.isSortAscending(SORT_TYPE.SORT_DATE);

        reSort();
    }

    private void onDelete(final List<MessageInfoHolder> holders)
    {
        final List<Message> messages = new ArrayList<Message>(holders.size());
        for (final MessageInfoHolder holder : holders)
        {
            messages.add(holder.message);
        }
        mAdapter.removeMessages(holders);
        mController.deleteMessages(messages.toArray(new Message[messages.size()]), null);
    }

    private void onMove(MessageInfoHolder holder)
    {
        if (mController.isMoveCapable(holder.message.getFolder().getAccount()) == false)
        {
            return;
        }

        if (mController.isMoveCapable(holder.message) == false)
        {
            Toast toast = Toast.makeText(this, R.string.move_copy_cannot_copy_unsynced_message, Toast.LENGTH_LONG);
            toast.show();
            return;
        }

        final Account account = holder.message.getFolder().getAccount();

        Intent intent = new Intent(this, ChooseFolder.class);
        intent.putExtra(ChooseFolder.EXTRA_ACCOUNT, account.getUuid());
        intent.putExtra(ChooseFolder.EXTRA_CUR_FOLDER, holder.folder.name);
        intent.putExtra(ChooseFolder.EXTRA_SEL_FOLDER, account.getLastSelectedFolderName());
        intent.putExtra(ChooseFolder.EXTRA_MESSAGE, holder.message.makeMessageReference());
        startActivityForResult(intent, ACTIVITY_CHOOSE_FOLDER_MOVE);
    }

    private void onArchive(MessageInfoHolder holder)
    {
        if (mController.isMoveCapable(holder.message.getFolder().getAccount()) == false)
        {
            return;
        }

        if (mController.isMoveCapable(holder.message) == false)
        {
            Toast toast = Toast.makeText(this, R.string.move_copy_cannot_copy_unsynced_message, Toast.LENGTH_LONG);
            toast.show();
            return;
        }

        onMoveChosen(holder, holder.message.getFolder().getAccount().getArchiveFolderName());
    }

    private void onSpam(MessageInfoHolder holder)
    {
        if (mController.isMoveCapable(holder.message.getFolder().getAccount()) == false)
        {
            return;
        }

        if (mController.isMoveCapable(holder.message) == false)
        {
            Toast toast = Toast.makeText(this, R.string.move_copy_cannot_copy_unsynced_message, Toast.LENGTH_LONG);
            toast.show();
            return;
        }

        onMoveChosen(holder, holder.message.getFolder().getAccount().getSpamFolderName());
    }

    private void onCopy(MessageInfoHolder holder)
    {
        if (mController.isCopyCapable(holder.message.getFolder().getAccount()) == false)
        {
            return;
        }

        if (mController.isCopyCapable(holder.message) == false)
        {
            Toast toast = Toast.makeText(this, R.string.move_copy_cannot_copy_unsynced_message, Toast.LENGTH_LONG);
            toast.show();
            return;
        }

        final Account account = holder.message.getFolder().getAccount();

        Intent intent = new Intent(this, ChooseFolder.class);
        intent.putExtra(ChooseFolder.EXTRA_ACCOUNT, account.getUuid());
        intent.putExtra(ChooseFolder.EXTRA_CUR_FOLDER, holder.folder.name);
        intent.putExtra(ChooseFolder.EXTRA_SEL_FOLDER, account.getLastSelectedFolderName());
        intent.putExtra(ChooseFolder.EXTRA_MESSAGE, holder.message.makeMessageReference());
        startActivityForResult(intent, ACTIVITY_CHOOSE_FOLDER_COPY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (resultCode != RESULT_OK)
            return;

        switch (requestCode)
        {
            case ACTIVITY_CHOOSE_FOLDER_MOVE:
            case ACTIVITY_CHOOSE_FOLDER_COPY:
            {
                if (data == null)
                    return;

                final String destFolderName = data.getStringExtra(ChooseFolder.EXTRA_NEW_FOLDER);
                final MessageReference ref = (MessageReference)data.getSerializableExtra(ChooseFolder.EXTRA_MESSAGE);
                final MessageInfoHolder m = mAdapter.getMessage(ref);

                if ((destFolderName != null) && (m != null))
                {
                    final Account account = m.message.getFolder().getAccount();

                    account.setLastSelectedFolderName(destFolderName);

                    switch (requestCode)
                    {
                        case ACTIVITY_CHOOSE_FOLDER_MOVE:
                            onMoveChosen(m, destFolderName);
                            break;

                        case ACTIVITY_CHOOSE_FOLDER_COPY:
                            onCopyChosen(m, destFolderName);
                            break;
                    }
                }
                break;
            }
            case ACTIVITY_CHOOSE_FOLDER_MOVE_BATCH:
            case ACTIVITY_CHOOSE_FOLDER_COPY_BATCH:
            {
                final String destFolderName = data.getStringExtra(ChooseFolder.EXTRA_NEW_FOLDER);
                final String accountUuid = data.getStringExtra(ChooseFolder.EXTRA_ACCOUNT);
                final Account account = Preferences.getPreferences(this).getAccount(accountUuid);

                account.setLastSelectedFolderName(destFolderName);

                switch (requestCode)
                {
                    case ACTIVITY_CHOOSE_FOLDER_MOVE_BATCH:
                        onMoveChosenBatch(destFolderName);
                        break;

                    case ACTIVITY_CHOOSE_FOLDER_COPY_BATCH:
                        onCopyChosenBatch(destFolderName);
                        break;
                }
            }
        }
    }

    private void onMoveChosen(MessageInfoHolder holder, String folderName)
    {
        if (mController.isMoveCapable(holder.message.getFolder().getAccount()) == true && folderName != null)
        {
            if (K9.FOLDER_NONE.equalsIgnoreCase(folderName))
            {
                return;
            }
            mAdapter.removeMessage(holder);
            mController.moveMessage(holder.message.getFolder().getAccount(), holder.message.getFolder().getName(), holder.message, folderName, null);
        }
    }

    private void onCopyChosen(MessageInfoHolder holder, String folderName)
    {
        if (mController.isCopyCapable(holder.message.getFolder().getAccount()) == true && folderName != null)
        {
            mController.copyMessage(holder.message.getFolder().getAccount(),
                                    holder.message.getFolder().getName(), holder.message, folderName, null);
        }
    }

    private void onReply(MessageInfoHolder holder)
    {
        MessageCompose.actionReply(this, holder.message.getFolder().getAccount(), holder.message, false, null);
    }

    private void onReplyAll(MessageInfoHolder holder)
    {
        MessageCompose.actionReply(this, holder.message.getFolder().getAccount(), holder.message, true, null);
    }

    private void onForward(MessageInfoHolder holder)
    {
        MessageCompose.actionForward(this, holder.message.getFolder().getAccount(), holder.message, null);
    }

    private void onMarkAllAsRead(final Account account, final String folder)
    {
        showDialog(DIALOG_MARK_ALL_AS_READ);
    }

    private void onExpunge(final Account account, String folderName)
    {
        mController.expunge(account, folderName, null);
    }

    @Override
    public Dialog onCreateDialog(int id)
    {
        switch (id)
        {
            case DIALOG_MARK_ALL_AS_READ:
                return createMarkAllAsReadDialog();
        }

        return super.onCreateDialog(id);
    }

    @Override
    public void onPrepareDialog(int id, Dialog dialog)
    {
        switch (id)
        {
            case DIALOG_MARK_ALL_AS_READ:
            {
                if (mCurrentFolder != null)
                {
                    ((AlertDialog)dialog).setMessage(getString(R.string.mark_all_as_read_dlg_instructions_fmt,
                                                     mCurrentFolder.displayName));
                }
                break;
            }
            default:
            {
                super.onPrepareDialog(id, dialog);
            }
        }
    }

    private Dialog createMarkAllAsReadDialog()
    {
        return new AlertDialog.Builder(this)
               .setTitle(R.string.mark_all_as_read_dlg_title)
               .setMessage(getString(R.string.mark_all_as_read_dlg_instructions_fmt,
                                     mCurrentFolder.displayName))
               .setPositiveButton(R.string.okay_action, new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int whichButton)
            {
                dismissDialog(DIALOG_MARK_ALL_AS_READ);

                try
                {
                    mController.markAllMessagesRead(mAccount, mCurrentFolder.name);

                    synchronized (mAdapter.messages)
                    {
                        for (MessageInfoHolder holder : mAdapter.messages)
                        {
                            holder.read = true;
                        }
                    }
                    mHandler.sortMessages();
                }
                catch (Exception e)
                {
                    // Ignore
                }
            }
        })
               .setNegativeButton(R.string.cancel_action, new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int whichButton)
            {
                dismissDialog(DIALOG_MARK_ALL_AS_READ);
            }
        })
               .create();
    }

    private void setReadStatus(final List<MessageInfoHolder> holders, final boolean read)
    {
        if (holders.isEmpty())
        {
            return;
        }
        if (mQueryString != null)
        {
            // TODO handle mixed folders/accounts mode (integrated INBOX)
            return;
        }
        Folder folder = null;
        final List<String> uids = new ArrayList<String>(holders.size());
        for (final MessageInfoHolder holder : holders)
        {
            if (folder == null)
            {
                folder = holder.message.getFolder();
            }
            uids.add(holder.uid);
            holder.read = read;
        }
        mController.setFlag(folder.getAccount(), folder.getName(), uids.toArray(new String[uids.size()]), Flag.SEEN, read);
        mHandler.sortMessages();
    }

    private void setFlagStatus(final List<MessageInfoHolder> holders, boolean enabled)
    {
        if (holders.isEmpty())
        {
            return;
        }
        if (mQueryString != null)
        {
            // TODO handle mixed folders/accounts mode (integrated INBOX)
            return;
        }
        Folder folder = null;
        final List<String> uids = new ArrayList<String>(holders.size());
        for (final MessageInfoHolder holder : holders)
        {
            if (folder == null)
            {
                folder = holder.message.getFolder();
            }
            uids.add(holder.uid);
            holder.flagged = enabled;
        }
        mController.setFlag(folder.getAccount(), folder.getName(), uids.toArray(new String[uids.size()]), Flag.FLAGGED, enabled);
        mHandler.sortMessages();
    }

    private void checkMail(Account account, String folderName)
    {
        mController.synchronizeMailbox(account, folderName, mAdapter.mListener, null);
        sendMail(account);
    }

    private void sendMail(Account account)
    {
        mController.sendPendingMessages(account, mAdapter.mListener);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        int itemId = item.getItemId();
        switch (itemId)
        {
            case R.id.compose:
            {
                onCompose();
                return true;
            }
            case R.id.accounts:
            {
                onAccounts();
                return true;
            }
            case R.id.set_sort_date:
            {
                changeSort(SORT_TYPE.SORT_DATE);
                return true;
            }
            case R.id.set_sort_subject:
            {
                changeSort(SORT_TYPE.SORT_SUBJECT);
                return true;
            }
            case R.id.set_sort_sender:
            {
                changeSort(SORT_TYPE.SORT_SENDER);
                return true;
            }
            case R.id.set_sort_flag:
            {
                changeSort(SORT_TYPE.SORT_FLAGGED);
                return true;
            }
            case R.id.set_sort_unread:
            {
                changeSort(SORT_TYPE.SORT_UNREAD);
                return true;
            }
            case R.id.set_sort_attach:
            {
                changeSort(SORT_TYPE.SORT_ATTACHMENT);
                return true;
            }
            case R.id.set_group_by_none:
                mAdapter.mMessageGrouper = new SingletonMessageGrouper();
                mAdapter.mGroupLessMode = true;
                reSort();
                break;
            case R.id.set_group_by_thread:
                mAdapter.mMessageGrouper = new ThreadMessageGrouper();
                mAdapter.mGroupLessMode = false;
                reSort();
                break;
            case R.id.set_group_by_sender:
                // TODO
                break;
            case R.id.set_group_by_date:
                // TODO
                break;
            case R.id.select_all:
            case R.id.batch_select_all:
            {
                setAllSelected(true);
                toggleBatchButtons();
                return true;
            }
            case R.id.batch_deselect_all:
            {
                setAllSelected(false);
                toggleBatchButtons();
                return true;
            }
            case R.id.batch_delete_op:
            {
                deleteSelected();
                return true;
            }
            case R.id.batch_mark_read_op:
            {
                flagSelected(Flag.SEEN, true);
                return true;
            }
            case R.id.batch_mark_unread_op:
            {
                flagSelected(Flag.SEEN, false);
                return true;
            }
            case R.id.batch_flag_op:
            {
                flagSelected(Flag.FLAGGED, true);
                return true;
            }
            case R.id.batch_unflag_op:
            {
                flagSelected(Flag.FLAGGED, false);
                return true;
            }
            case R.id.settings:
            {
                if (mQueryString == null)
                {
                    break;
                }

                /*
                 * Fall-through in search results view. Otherwise a sub-menu
                 * with only one option would be opened.
                 */
            }
            case R.id.app_settings:
            {
                onEditPrefs();
                return true;
            }
        }

        if (mQueryString != null)
        {
            // None of the options after this point are "safe" for search results
            //TODO: This is not true for "unread" and "starred" searches in regular folders
            return false;
        }

        switch (itemId)
        {
            case R.id.check_mail:
            {
                if (mFolderName != null)
                {
                    checkMail(mAccount, mFolderName);
                }
                return true;
            }
            case R.id.send_messages:
            {
                sendMail(mAccount);
                return true;
            }
            case R.id.list_folders:
            {
                onShowFolderList();
                return true;
            }
            case R.id.mark_all_as_read:
            {
                if (mFolderName != null)
                {
                    onMarkAllAsRead(mAccount, mFolderName);
                }
                return true;
            }
            case R.id.folder_settings:
            {
                if (mFolderName != null)
                {
                    FolderSettings.actionSettings(this, mAccount, mFolderName);
                }
                return true;
            }
            case R.id.account_settings:
            {
                onEditAccount();
                return true;
            }
            case R.id.batch_copy_op:
            {
                onCopyBatch();
                return true;
            }
            case R.id.batch_archive_op:
            {
                onArchiveBatch();
                return true;
            }
            case R.id.batch_spam_op:
            {
                onSpamBatch();
                return true;
            }
            case R.id.batch_move_op:
            {
                onMoveBatch();
                return true;
            }
            case R.id.expunge:
            {
                if (mCurrentFolder != null)
                {
                    onExpunge(mAccount, mCurrentFolder.name);
                }
                return true;
            }
            default:
            {
                return super.onOptionsItemSelected(item);
            }
        }
    }

    private final int[] batch_ops = { R.id.batch_copy_op, R.id.batch_delete_op, R.id.batch_flag_op,
                                      R.id.batch_unflag_op, R.id.batch_mark_read_op, R.id.batch_mark_unread_op,
                                      R.id.batch_archive_op, R.id.batch_spam_op, R.id.batch_move_op,
                                      R.id.batch_select_all, R.id.batch_deselect_all
                                    };

    private void setOpsState(Menu menu, boolean state, boolean enabled)
    {
        for (int id : batch_ops)
        {
            menu.findItem(id).setVisible(state);
            menu.findItem(id).setEnabled(enabled);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
        boolean anySelected = anySelected();

        menu.findItem(R.id.select_all).setVisible(! anySelected);
        menu.findItem(R.id.batch_ops).setVisible(anySelected);

        setOpsState(menu, true, anySelected);

        if (mQueryString != null)
        {
            menu.findItem(R.id.mark_all_as_read).setVisible(false);
            menu.findItem(R.id.list_folders).setVisible(false);
            menu.findItem(R.id.expunge).setVisible(false);
            menu.findItem(R.id.batch_archive_op).setVisible(false);
            menu.findItem(R.id.batch_spam_op).setVisible(false);
            menu.findItem(R.id.batch_move_op).setVisible(false);
            menu.findItem(R.id.batch_copy_op).setVisible(false);
            menu.findItem(R.id.check_mail).setVisible(false);
            menu.findItem(R.id.send_messages).setVisible(false);
        }
        else
        {
            if (mCurrentFolder != null && mCurrentFolder.outbox)
            {
                menu.findItem(R.id.check_mail).setVisible(false);
            }
            else
            {
                menu.findItem(R.id.send_messages).setVisible(false);
            }

            if (mCurrentFolder != null && K9.ERROR_FOLDER_NAME.equals(mCurrentFolder.name))
            {
                menu.findItem(R.id.expunge).setVisible(false);
            }
            if (K9.FOLDER_NONE.equalsIgnoreCase(mAccount.getArchiveFolderName()))
            {
                menu.findItem(R.id.batch_archive_op).setVisible(false);
            }
            if (K9.FOLDER_NONE.equalsIgnoreCase(mAccount.getSpamFolderName()))
            {
                menu.findItem(R.id.batch_spam_op).setVisible(false);
            }
        }

        boolean newFlagState = computeBatchDirection(true);
        boolean newReadState = computeBatchDirection(false);
        menu.findItem(R.id.batch_flag_op).setVisible(newFlagState);
        menu.findItem(R.id.batch_unflag_op).setVisible(!newFlagState);
        menu.findItem(R.id.batch_mark_read_op).setVisible(newReadState);
        menu.findItem(R.id.batch_mark_unread_op).setVisible(!newReadState);
        menu.findItem(R.id.batch_deselect_all).setVisible(anySelected);
        menu.findItem(R.id.batch_select_all).setEnabled(true);

        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.message_list_option, menu);

        return true;
    }

    @Override
    public boolean onContextItemSelected(final MenuItem item)
    {
        final MessageInfoHolder holder = mSelectedMessage;
        final MessageGroup<MessageInfoHolder> group = mSelectedGroup;

        // don't need them anymore
        mSelectedMessage = null;
        mSelectedGroup = null;

        if (holder != null && group == null)
        {
            return onContextItemSelectedForMessage(item, holder);
        }
        else if (holder == null && group != null)
        {
            return onContextItemSelectedForGroup(item, group);
        }
        else
        {
            return super.onContextItemSelected(item);
        }

    }

    /**
     * @param item
     *            Never <code>null</code>.
     * @param holder
     *            Never <code>null</code>.
     * @return See {@link Activity#onContextItemSelected(MenuItem)}
     */
    private boolean onContextItemSelectedForMessage(MenuItem item, final MessageInfoHolder holder)
    {
        switch (item.getItemId())
        {
            case R.id.open:
            {
                onOpenMessage(holder);
                break;
            }
            case R.id.select:
            {
                setSelected(Collections.singletonList(holder), true);
                break;
            }
            case R.id.deselect:
            {
                setSelected(Collections.singletonList(holder), false);
                break;
            }
            case R.id.delete:
            {
                onDelete(Collections.singletonList(holder));
                break;
            }
            case R.id.reply:
            {
                onReply(holder);
                break;
            }
            case R.id.reply_all:
            {
                onReplyAll(holder);
                break;
            }
            case R.id.forward:
            {
                onForward(holder);
                break;
            }
            case R.id.mark_as_read:
            {
                setReadStatus(Collections.singletonList(holder), !holder.read);
                break;
            }
            case R.id.flag:
            {
                setFlagStatus(Collections.singletonList(holder), !holder.flagged);
                break;
            }
            case R.id.archive:
            {
                onArchive(holder);
                break;
            }
            case R.id.spam:
            {
                onSpam(holder);
                break;
            }
            case R.id.move:
            {
                onMove(holder);
                break;
            }
            case R.id.copy:
            {
                onCopy(holder);
                break;
            }
            case R.id.send_alternate:
            {
                onSendAlternate(mAccount, holder);
                break;
            }
            case R.id.same_sender:
            {
                MessageList.actionHandle(MessageList.this,
                                         "From "+holder.sender, holder.sender, true,
                                         null, null);
                break;
            }
        }
        return super.onContextItemSelected(item);
    }

    /**
     * @param item
     *            Never <code>null</code>.
     * @param group
     *            Never <code>null</code>.
     * @return See {@link Activity#onContextItemSelected(MenuItem)}
     */
    private boolean onContextItemSelectedForGroup(final MenuItem item,
            final MessageGroup<MessageInfoHolder> group)
    {
        final List<MessageInfoHolder> holders = new ArrayList<MessageInfoHolder>(group.getMessages().size());
        for (final MessageInfo<MessageInfoHolder> info : group.getMessages())
        {
            holders.add(info.getTag());
        }
        final int itemId = item.getItemId();
        switch (itemId)
        {
            case R.id.expand:
            {
                int i = findGroupPosition(group);
                if (i >= 0)
                {
                    mListView.expandGroup(i);
                }
                break;
            }
            case R.id.collapse:
            {
                int i = findGroupPosition(group);
                if (i >= 0)
                {
                    mListView.collapseGroup(i);
                }
                break;
            }
            case R.id.expand_all:
                expandAll();
                break;
            case R.id.collapse_all:
                collapseAll();
                break;
            case R.id.group_delete:
                onDelete(holders);
                break;
            case R.id.group_select:
            case R.id.group_deselect:
                setSelected(holders, itemId == R.id.group_select);
                if (itemId == R.id.group_select)
                {
                    // display selection to user
                    int i = findGroupPosition(group);
                    if (i >= 0)
                    {
                        mListView.expandGroup(i);
                    }
                }
                break;
            case R.id.group_flag:
            case R.id.group_unflag:
                setFlagStatus(holders, itemId == R.id.group_flag);
                break;
            case R.id.group_mark_as_read:
            case R.id.group_mark_as_unread:
                setReadStatus(holders, itemId == R.id.group_mark_as_read);
                break;
            case R.id.group_copy:
                // TODO
                break;
            case R.id.group_move:
                // TODO
                break;
            case R.id.group_archive:
                // TODO
                break;
            case R.id.group_spam:
                // TODO
                break;
            default:
                break;
        }
        return super.onContextItemSelected(item);
    }

    /**
     * @param group
     *            Never <code>null</code>.
     * @return -1 if not found
     */
    private int findGroupPosition(final MessageGroup<MessageInfoHolder> group)
    {
        final int groupCount = mAdapter.getGroupCount();
        if (groupCount > 0)
        {
            int i = 0;
            for (MessageGroup<MessageInfoHolder> otherGroup; i < groupCount; i++)
            {
                otherGroup = mAdapter.getGroup(i);
                if (group.getId() == otherGroup.getId())
                {
                    break;
                }
            }
            return i;
        }
        else
        {
            return -1;
        }
    }

    private void collapseAll()
    {
        final int groupCount = mAdapter.getGroupCount() - MessageListAdapter.NON_MESSAGE_ITEMS;
        for (int i = 0; i < groupCount; i++)
        {
            if (mListView.isGroupExpanded(i))
            {
                mListView.collapseGroup(i);
            }
        }
    }

    private void expandAll()
    {
        final int groupCount = mAdapter.getGroupCount() - MessageListAdapter.NON_MESSAGE_ITEMS;
        for (int i = 0; i < groupCount; i++)
        {
            if (!mListView.isGroupExpanded(i))
            {
                mListView.expandGroup(i);
            }
        }
    }

    public void onSendAlternate(Account account, MessageInfoHolder holder)
    {
        mController.sendAlternate(this, account, holder.message);
    }

    public void showProgressIndicator(boolean status)
    {
        setProgressBarIndeterminateVisibility(status);
        ProgressBar bar = (ProgressBar)mListView.findViewById(R.id.message_list_progress);
        if (bar == null)
        {
            return;
        }

        bar.setIndeterminate(true);
        if (status)
        {
            bar.setVisibility(ProgressBar.VISIBLE);
        }
        else
        {
            bar.setVisibility(ProgressBar.INVISIBLE);
        }
    }

    class MyGestureDetector extends SimpleOnGestureListener
    {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
        {
            if (e2 == null || e1 == null)
                return true;

            float deltaX = e2.getX() - e1.getX(),
                           deltaY = e2.getY() - e1.getY();

            boolean movedAcross = (Math.abs(deltaX) > Math.abs(deltaY * 4));
            boolean steadyHand = (Math.abs(deltaX / deltaY) > 2);

            if (movedAcross && steadyHand)
            {
                boolean selected = (deltaX > 0);
                int position = mListView.pointToPosition((int)e1.getX(), (int)e1.getY());

                if (position != AdapterView.INVALID_POSITION)
                {
                    final Object item = mListView.getItemAtPosition(position);
                    if (item instanceof MessageInfoHolder) {
                        MessageInfoHolder msgInfoHolder = (MessageInfoHolder) item;

                        if (msgInfoHolder != null && msgInfoHolder.selected != selected)
                        {
                            msgInfoHolder.selected = selected;
                            mSelectedCount += (selected ? 1 : -1);
                            mAdapter.notifyDataSetChanged();
                            toggleBatchButtons();
                        }
                    }
                }
            }

            return false;
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
    {
        // reset remembered selection
        mSelectedGroup = null;
        mSelectedMessage = null;

        // hide by default
        menu.setGroupVisible(R.id.message_group, false);
        menu.setGroupVisible(R.id.single_message, false);

        if (menuInfo instanceof ExpandableListView.ExpandableListContextMenuInfo)
        {
            final ExpandableListView.ExpandableListContextMenuInfo info = (ExpandableListView.ExpandableListContextMenuInfo) menuInfo;
            final long packedPosition = info.packedPosition;
            final int packedPositionType = ExpandableListView.getPackedPositionType(packedPosition);

            switch (packedPositionType)
            {
                case ExpandableListView.PACKED_POSITION_TYPE_GROUP:
                    // group
                    final int groupPosition = ExpandableListView
                            .getPackedPositionGroup(packedPosition);
                    mSelectedGroup = mAdapter.getGroup(groupPosition);

                    if (mSelectedGroup == null)
                    {
                        break;
                    }

                    onCreateContextMenuForGroup(menu, v, menuInfo, mSelectedGroup, groupPosition);

                    break;
                case ExpandableListView.PACKED_POSITION_TYPE_CHILD:
                    // message
                    final int packedPositionChild = ExpandableListView
                            .getPackedPositionChild(packedPosition);
                    final int packedPositionGroup = ExpandableListView
                            .getPackedPositionGroup(packedPosition);

                    // remember which message was originally selected, in case the list changes while the
                    // dialog is up
                    mSelectedMessage = mAdapter.getChild(packedPositionGroup, packedPositionChild);

                    if (mSelectedMessage == null)
                    {
                        break;
                    }

                    onCreateContextMenuForMessage(menu, mSelectedMessage);

                    break;
            }
        }
    }

    /**
     * @param menu
     *            Never <code>null</code>.
     * @param message
     *            Never <code>null</code>.
     * @see Activity#onCreateContextMenu(ContextMenu, View, ContextMenuInfo)
     */
    private void onCreateContextMenuForMessage(final ContextMenu menu, final MessageInfoHolder message)
    {
        getMenuInflater().inflate(R.menu.message_list_context, menu);

        menu.setHeaderTitle((CharSequence) message.subject);

        menu.setGroupVisible(R.id.single_message, true);

        if (message.read)
        {
            menu.findItem(R.id.mark_as_read).setTitle(R.string.mark_as_unread_action);
        }

        if (message.flagged)
        {
            menu.findItem(R.id.flag).setTitle(R.string.unflag_action);
        }

        Account account = message.message.getFolder().getAccount();
        if (mController.isCopyCapable(account) == false)
        {
            menu.findItem(R.id.copy).setVisible(false);
        }

        if (mController.isMoveCapable(account) == false)
        {
            menu.findItem(R.id.move).setVisible(false);
            menu.findItem(R.id.archive).setVisible(false);
            menu.findItem(R.id.spam).setVisible(false);
        }

        if (K9.FOLDER_NONE.equalsIgnoreCase(account.getArchiveFolderName()))
        {
            menu.findItem(R.id.archive).setVisible(false);
        }
        if (K9.FOLDER_NONE.equalsIgnoreCase(account.getSpamFolderName()))
        {
            menu.findItem(R.id.spam).setVisible(false);
        }

        if (message.selected)
        {
            menu.findItem(R.id.select).setVisible(false);
            menu.findItem(R.id.deselect).setVisible(true);
        }
        else
        {
            menu.findItem(R.id.select).setVisible(true);
            menu.findItem(R.id.deselect).setVisible(false);
        }
    }

    /**
     * @param menu
     *            Never <code>null</code>.
     * @param v
     *            Never <code>null</code>.
     * @param menuInfo
     *            Never <code>null</code>.
     * @param group
     *            Never <code>null</code>.
     * @param groupPosition
     * @see Activity#onCreateContextMenu(ContextMenu, View, ContextMenuInfo)
     */
    private void onCreateContextMenuForGroup(final ContextMenu menu, final View v, final ContextMenuInfo menuInfo,
            final MessageGroup<MessageInfoHolder> group, final int groupPosition)
    {
        getMenuInflater().inflate(R.menu.message_list_context, menu);
        menu.setHeaderTitle(group.getSubject());
        menu.setGroupVisible(R.id.message_group, true);

        if (mListView.isGroupExpanded(groupPosition))
        {
            menu.findItem(R.id.expand).setVisible(false);
            menu.findItem(R.id.collapse).setVisible(true);
        }
        else
        {
            menu.findItem(R.id.expand).setVisible(true);
            menu.findItem(R.id.collapse).setVisible(false);
        }
        boolean unread = false;
        boolean read = false;
        boolean unflagged = false;
        boolean flagged = false;
        boolean selected = false;
        boolean unselected = false;
        for (final MessageInfo<MessageInfoHolder> info : group.getMessages())
        {
            final MessageInfoHolder holder = info.getTag();
            if (!read && holder.read)
            {
                read = true;
            }
            else if (!unread && !holder.read)
            {
                unread = true;
            }
            if (!flagged && holder.flagged)
            {
                flagged = true;
            }
            else if (!unflagged && !holder.flagged)
            {
                unflagged = true;
            }
            if (!selected && holder.selected)
            {
                selected = true;
            }
            else if (!unselected && !holder.selected)
            {
                unselected = true;
            }
        }
        menu.findItem(R.id.group_mark_as_read).setVisible(unread);
        menu.findItem(R.id.group_mark_as_unread).setVisible(read);
        menu.findItem(R.id.group_flag).setVisible(unflagged);
        menu.findItem(R.id.group_unflag).setVisible(flagged);
        menu.findItem(R.id.group_select).setVisible(unselected);
        menu.findItem(R.id.group_deselect).setVisible(selected);
    }

    class MessageListAdapter extends BaseExpandableListAdapter implements SectionIndexer
    {
        private final List<MessageInfoHolder> messages = java.util.Collections.synchronizedList(new ArrayList<MessageInfoHolder>());

        private final List<MessageGroup<MessageInfoHolder>> mGroups = Collections
                .synchronizedList(new ArrayList<MessageGroup<MessageInfoHolder>>());

        private final ActivityListener mListener = new ActivityListener()
        {
            @Override
            public void synchronizeMailboxStarted(Account account, String folder)
            {
                super.synchronizeMailboxStarted(account, folder);

                if (updateForMe(account, folder))
                {
                    mHandler.progress(true);
                    mHandler.folderLoading(folder, true);
                }
                mHandler.refreshTitle();
            }
            @Override
            public void synchronizeMailboxHeadersProgress(Account account, String folder, int completed, int total)
            {
                super.synchronizeMailboxHeadersProgress(account,folder,completed, total);
                mHandler.refreshTitle();
            }

            @Override
            public void synchronizeMailboxHeadersFinished(Account account, String folder,
                    int total, int completed)
            {
                super.synchronizeMailboxHeadersFinished(account,folder, total, completed);
                mHandler.refreshTitle();
            }




            @Override
            public void synchronizeMailboxFinished(Account account, String folder,
                                                   int totalMessagesInMailbox, int numNewMessages)
            {
                super.synchronizeMailboxFinished(account, folder, totalMessagesInMailbox, numNewMessages);

                if (updateForMe(account, folder))
                {
                    mHandler.progress(false);
                    mHandler.folderLoading(folder, false);
                    mHandler.sortMessages();
                }
                mHandler.refreshTitle();
            }

            @Override
            public void synchronizeMailboxFailed(Account account, String folder, String message)
            {
                super.synchronizeMailboxFailed(account, folder, message);

                if (updateForMe(account, folder))
                {
                    mHandler.progress(false);
                    mHandler.folderLoading(folder, false);
                    mHandler.sortMessages();
                }
                mHandler.refreshTitle();
            }

            @Override
            public void sendPendingMessagesStarted(Account account)
            {
                super.sendPendingMessagesStarted(account);
                mHandler.refreshTitle();
            }

            @Override
            public void sendPendingMessagesCompleted(Account account)
            {
                super.sendPendingMessagesCompleted(account);
                mHandler.refreshTitle();
            }

            @Override
            public void sendPendingMessagesFailed(Account account)
            {
                super.sendPendingMessagesFailed(account);
                mHandler.refreshTitle();
            }

            @Override
            public void synchronizeMailboxProgress(Account account, String folder, int completed, int total)
            {
                super.synchronizeMailboxProgress(account, folder, completed, total);
                mHandler.refreshTitle();
            }

            @Override
            public void synchronizeMailboxAddOrUpdateMessage(Account account, String folder, Message message)
            {
                addOrUpdateMessage(account, folder, message, true);
            }

            @Override
            public void synchronizeMailboxRemovedMessage(Account account, String folder,Message message)
            {
                MessageInfoHolder holder = getMessage(message);
                if (holder == null)
                {
                    Log.w(K9.LOG_TAG, "Got callback to remove non-existent message with UID " + message.getUid());
                }
                else
                {
                    removeMessage(holder);
                }
            }

            @Override
            public void listLocalMessagesStarted(Account account, String folder)
            {
                if ((mQueryString != null && folder == null) ||
                        (account != null && account.equals(mAccount))
                   )
                {
                    mHandler.progress(true);
                    if (folder != null)
                    {
                        mHandler.folderLoading(folder, true);
                    }
                }
            }

            @Override
            public void listLocalMessagesFailed(Account account, String folder, String message)
            {
                if ((mQueryString != null && folder == null) ||
                        (account != null && account.equals(mAccount)))
                {
                    mHandler.sortMessages();
                    mHandler.progress(false);
                    if (folder != null)
                    {
                        mHandler.folderLoading(folder, false);
                    }
                }
            }

            @Override
            public void listLocalMessagesFinished(Account account, String folder)
            {
                if ((mQueryString != null && folder == null) ||
                        (account != null && account.equals(mAccount)))
                {
                    mHandler.sortMessages();
                    mHandler.progress(false);
                    if (folder != null)
                    {
                        mHandler.folderLoading(folder, false);
                    }
                }
            }

            @Override
            public void listLocalMessagesRemoveMessage(Account account, String folder,Message message)
            {
                MessageInfoHolder holder = getMessage(message);
                if (holder != null)
                {
                    removeMessage(holder);
                }
            }

            @Override
            public void listLocalMessagesAddMessages(Account account, String folder, List<Message> messages)
            {
                addOrUpdateMessages(account, folder, messages, false);
            }

            @Override
            public void listLocalMessagesUpdateMessage(Account account, String folder, Message message)
            {
                addOrUpdateMessage(account, folder, message, false);
            }

            @Override
            public void searchStats(AccountStats stats)
            {
                mUnreadMessageCount = stats.unreadMessageCount;
                mHandler.refreshTitle();
            }

            @Override
            public void folderStatusChanged(Account account, String folder, int unreadMessageCount)
            {
                super.folderStatusChanged(account, folder, unreadMessageCount);
                if (updateForMe(account, folder))
                {
                    mUnreadMessageCount = unreadMessageCount;
                    mHandler.refreshTitle();
                }
            }

            @Override
            public void pendingCommandsProcessing(Account account)
            {
                super.pendingCommandsProcessing(account);
                mHandler.refreshTitle();
            }

            @Override
            public void pendingCommandsFinished(Account account)
            {
                super.pendingCommandsFinished(account);
                mHandler.refreshTitle();
            }

            @Override
            public void pendingCommandStarted(Account account, String commandTitle)
            {
                super.pendingCommandStarted(account, commandTitle);
                mHandler.refreshTitle();
            }

            @Override
            public void pendingCommandCompleted(Account account, String commandTitle)
            {
                super.pendingCommandCompleted(account, commandTitle);
                mHandler.refreshTitle();
            }

            @Override
            public void messageUidChanged(Account account, String folder, String oldUid, String newUid)
            {
                MessageReference ref = new MessageReference();
                ref.accountUuid = account.getUuid();
                ref.folderName = folder;
                ref.uid = oldUid;

                MessageInfoHolder holder = getMessage(ref);
                if (holder != null)
                {
                    holder.uid = newUid;
                    holder.message.setUid(newUid);
                }
            }
        };

        private boolean updateForMe(Account account, String folder)
        {
            if ((account.equals(mAccount) && mFolderName != null && folder.equals(mFolderName)))
            {
                return true;
            }
            else
            {
                return false;
            }
        }

        private Drawable mAttachmentIcon;
        private Drawable mAnsweredIcon;
        private View footerView = null;

        private MessageGrouper mMessageGrouper;
        private boolean mGroupLessMode = false;

        private UiThrottler<Void> mThrottler;

        private boolean mGroupingInProgress = false;

        private MessageListAdapter()
        {
            mAttachmentIcon = getResources().getDrawable(R.drawable.ic_mms_attachment_small);
            mAnsweredIcon = getResources().getDrawable(R.drawable.ic_mms_answered_small);

            // TODO restore previous active/selected implementation
            mMessageGrouper = new ThreadMessageGrouper();

            mThrottler = new UiThrottler<Void>(MessageList.this, new Callable<Void>()
            {
                @Override
                public Void call()
                {
                    synchronizeGroups();
                    return null;
                }
            }, null); // not setting Executor now as we want to integrate into Activity onResume/onPause
            mThrottler.setCoolDownDuration(200L);
            mThrottler.setPostExecute(new Runnable()
            {
                @Override
                public void run()
                {
                    doNotifyDataSetChanged();
                }
            });
            mThrottler.setCompleted(new Runnable()
            {
                @Override
                public void run()
                {
                    mGroupingInProgress = false;
                    updateFooterView();
                    if (mGroupLessMode)
                    {
                        // making sure we expand the sole group in groupless mode
                        expandAll();
                    }
                    synchronizeFastScroll();
                }
            });
        }


        /**
         * Override the regular notifyDataSetChanged() method so that we can
         * throttle message list updates, regardless of how fast/repeatly we
         * call this method.
         * 
         * <p>
         * This prevent any computation from being a CPU-hog.
         * </p>
         * 
         * <p>
         * If you NEED to trigger immediate UI update, please use
         * {@link #doNotifyDataSetChanged()}
         * </p>
         * 
         * {@inheritDoc}
         */
        @Override
        public void notifyDataSetChanged()
        {
            // buffer dataset update otherwise we might get continous CPU
            // processing in case of repeated call!
            mGroupingInProgress = mCurrentFolder != null && mAccount != null && mCurrentFolder.loading;
            mThrottler.attempt();
        }

        /**
         * Actual (non-throttled) call to the regular
         * {@link BaseAdapter#notifyDataSetChanged()}
         */
        public void doNotifyDataSetChanged()
        {
            super.notifyDataSetChanged();
        }

        /**
         * 
         */
        private void synchronizeGroups()
        {
            final List<MessageInfo<MessageInfoHolder>> toGroup = new ArrayList<MessageInfo<MessageInfoHolder>>(
                    messages.size());
            synchronized (messages)
            {
                for (final MessageInfoHolder holder : messages)
                {
                    final MessageInfo<MessageInfoHolder> messageInfo = new MessageInfo<MessageInfoHolder>();
                    final Message message = holder.message;
                    try
                    {
                        messageInfo.setId(message.getMessageId());
                        final String[] references = message.getReferences();
                        if (references != null)
                        {
                            messageInfo.getReferences().addAll(getReferences(references));
                        }
                        final String[] inReplyTo = message.getHeader("In-Reply-To");
                        if (inReplyTo != null && inReplyTo.length > 0)
                        {
                            messageInfo.getReferences().add(inReplyTo[0]);
                        }
                    }
                    catch (MessagingException e)
                    {
                        // should not happen?
                        Log.w(K9.LOG_TAG, "Unable to retrieve header from "
                                + message, e);
                        continue;
                    }
                    messageInfo.setDate(holder.compareDate);
                    messageInfo.setSubject(holder.subject);

                    messageInfo.setTag(holder);

                    toGroup.add(messageInfo);
                }
            }
            final List<MessageGroup<MessageInfoHolder>> messageGroups = mMessageGrouper
                    .group(toGroup);

            mGroups.clear();
            mGroups.addAll(messageGroups);

            for (MessageGroup<MessageInfoHolder> messageGroup : messageGroups)
            {
                final List<MessageInfo<MessageInfoHolder>> groupMessages = messageGroup.getMessages();
                final MessageInfoHolder holder = groupMessages.get(0).getTag();
                // FIXME (circular reference)
                holder.group = messageGroup;
            }

            // TODO: sort group messages?
        }

        private void synchronizeFastScroll()
        {
            // only way to make getSections() invoked again: disable/enable back
            mListView.setFastScrollEnabled(false);
            mListView.setFastScrollEnabled(true);
        }

        private final Pattern splitter = Pattern.compile("\\s");
        /**
         * @param references
         * @return
         */
        private List<String> getReferences(final String[] references)
        {
            final List<String> result = new ArrayList<String>();
            for (final String reference : references)
            {
                List<String> split = Arrays.asList(splitter.split(reference));
                result.addAll(split);
            }
            for (final Iterator<String> iterator = result.iterator(); iterator.hasNext();)
            {
                String string = iterator.next();
                if (string.length() == 0)
                {
                    iterator.remove();
                }
            }
            return result;
        }
        public void removeMessages(List<MessageInfoHolder> holders)
        {
            if (holders != null)
            {
                mHandler.removeMessage(holders);
            }
        }

        public void removeMessage(MessageInfoHolder holder)
        {
            List<MessageInfoHolder> messages = new ArrayList<MessageInfoHolder>();
            messages.add(holder);
            removeMessages(messages);
        }

        private void addOrUpdateMessage(Account account, String folder, Message message, boolean verifyAgainstSearch)
        {
            List<Message> messages = new ArrayList<Message>();
            messages.add(message);
            addOrUpdateMessages(account, folder, messages, verifyAgainstSearch);
        }

        private void addOrUpdateMessages(Account account, String folder, List<Message> messages, boolean verifyAgainstSearch)
        {
            boolean needsSort = false;
            final List<MessageInfoHolder> messagesToAdd = new ArrayList<MessageInfoHolder>();
            List<MessageInfoHolder> messagesToRemove = new ArrayList<MessageInfoHolder>();
            List<Message> messagesToSearch = new ArrayList<Message>();

            for (Message message : messages)
            {
                MessageInfoHolder m = getMessage(message);
                if (message.isSet(Flag.DELETED))
                {
                    if (m != null)
                    {
                        messagesToRemove.add(m);
                    }
                }
                else if (m == null)
                {
                    if (updateForMe(account, folder))
                    {
                        m = new MessageInfoHolder(message);
                        messagesToAdd.add(m);
                    }
                    else
                    {
                        if (mQueryString != null)
                        {
                            if (verifyAgainstSearch)
                            {
                                messagesToSearch.add(message);
                            }
                            else
                            {
                                m = new MessageInfoHolder(message);
                                messagesToAdd.add(m);
                            }
                        }
                    }
                }
                else
                {
                    m.populate(message, new FolderInfoHolder(message.getFolder(), account), account);
                    needsSort = true;
                }
            }

            if (messagesToSearch.size() > 0)
            {
                mController.searchLocalMessages(mAccountUuids, mFolderNames, messagesToSearch.toArray(EMPTY_MESSAGE_ARRAY), mQueryString, mIntegrate, mQueryFlags, mForbiddenFlags,
                                                new MessagingListener()
                {
                    @Override
                    public void listLocalMessagesAddMessages(Account account, String folder, List<Message> messages)
                    {
                        addOrUpdateMessages(account, folder, messages, false);
                    }
                });
            }

            if (messagesToRemove.size() > 0)
            {
                removeMessages(messagesToRemove);
            }

            if (messagesToAdd.size() > 0)
            {
                mHandler.addMessages(messagesToAdd);
            }

            if (needsSort)
            {
                mHandler.sortMessages();
                mHandler.resetUnreadCount();
            }
        }

        public MessageInfoHolder getMessage(Message message)
        {
            return getMessage(message.makeMessageReference());
        }

        // XXX TODO - make this not use a for loop
        public MessageInfoHolder getMessage(MessageReference messageReference)
        {
            synchronized (mAdapter.messages)
            {
                for (MessageInfoHolder holder : mAdapter.messages)
                {
                    /*
                     * 2010-06-21 - cketti
                     * Added null pointer check. Not sure what's causing 'holder'
                     * to be null. See log provided in issue 1749, comment #15.
                     *
                     * Please remove this comment once the cause was found and the
                     * bug(?) fixed.
                     */
                    if ((holder != null) && holder.message.equalsReference(messageReference))
                    {
                        return holder;
                    }
                }
            }
            return null;
        }

        public FolderInfoHolder getFolder(String folder, Account account)
        {
            LocalFolder local_folder = null;
            try
            {
                LocalStore localStore = account.getLocalStore();
                local_folder = localStore.getFolder(folder);
                return new FolderInfoHolder((Folder)local_folder, account);
            }
            catch (Exception e)
            {
                Log.e(K9.LOG_TAG, "getFolder(" + folder + ") goes boom: ",e);
                return null;
            }
            finally
            {
                if (local_folder != null)
                {
                    local_folder.close();
                }
            }
        }

        private static final int NON_MESSAGE_ITEMS = 1;

        private final View.OnClickListener flaggedClickListener = new View.OnClickListener()
        {
            @Override
            public void onClick(final View v)
            {
                if (v.getId() != R.id.flagged)
                {
                    return;
                }
                // Perform action on clicks
                final int position = mListView.getPositionForView(v);
                if (position == AdapterView.INVALID_POSITION)
                {
                    return;
                }
                final Object item = mListView.getItemAtPosition(position);
                if (item instanceof MessageInfoHolder)
                {
                    final MessageInfoHolder message = (MessageInfoHolder) item;
                    setFlagStatus(Collections.singletonList(message), !message.flagged);
                }
            }
        };

//        @Override
//        public int getCount()
//        {
//            return groups.size() + NON_MESSAGE_ITEMS;
//        }

//        @Override
//        public long getItemId(int position)
//        {
//            try
//            {
//                // FIXME
//                MessageInfoHolder messageHolder = getItem(position).getMessages().get(0).getTag();
//                if (messageHolder != null)
//                {
//                    return ((LocalStore.LocalMessage)  messageHolder.message).getId();
//                }
//            }
//            catch (Exception e)
//            {
//                Log.i(K9.LOG_TAG,"getItemId("+position+") ",e);
//            }
//            return -1;
//        }

//        @Override
//        public MessageGroup<MessageInfoHolder> getItem(int position)
//        {
//            try
//            {
//                synchronized (groups)
//                {
//                    if (position < groups.size())
//                    {
//                        return groups.get(position);
//                    }
//                }
//            }
//            catch (Exception e)
//            {
//                Log.e(K9.LOG_TAG, "getItem(" + position + "), but groups.size() = " + groups.size(), e);
//            }
//            return null;
//        }

//        @Override
//        public View getView(int position, View convertView, ViewGroup parent)
//        {
//
//            if (position == groups.size())
//            {
//                return getFooterView(position, convertView, parent);
//            }
//            else
//            {
//                return getItemView(position, convertView, parent);
//            }
//        }

        private View getItemView(int groupPosition, int position, View convertView, ViewGroup parent)
        {
            MessageInfoHolder message = getChild(groupPosition, position);
            View view;

            if ((convertView != null) && (convertView.getId() == R.layout.message_list_item))
            {
                view = convertView;
            }
            else
            {
                if (mTouchView)
                {
                    view = mInflater.inflate(R.layout.message_list_item_touchable, parent, false);
                    view.setId(R.layout.message_list_item);
                }
                else
                {
                    view = mInflater.inflate(R.layout.message_list_item, parent, false);
                    view.setId(R.layout.message_list_item);
                }
            }

            MessageViewHolder holder = (MessageViewHolder) view.getTag();

            if (holder == null)
            {
                holder = new MessageViewHolder();
                holder.subject = (TextView) view.findViewById(R.id.subject);
                holder.from = (TextView) view.findViewById(R.id.from);
                holder.date = (TextView) view.findViewById(R.id.date);
                holder.chip = view.findViewById(R.id.chip);
                holder.preview = (TextView) view.findViewById(R.id.preview);
                holder.selected = (CheckBox) view.findViewById(R.id.selected_checkbox);
                holder.flagged = (CheckBox) view.findViewById(R.id.flagged);

                holder.flagged.setOnClickListener(flaggedClickListener);

                if (mStars == false)
                {
                    holder.flagged.setVisibility(View.GONE);
                }

                if (mCheckboxes == true)
                {
                    holder.selected.setVisibility(View.VISIBLE);
                }

                if (holder.selected != null)
                {
                    holder.selected.setOnCheckedChangeListener(holder);
                }

                view.setTag(holder);
            }

            if (message != null)
            {
                bindView(groupPosition, position, view, holder, message);
            }
            else
            {
                // TODO is this branch ever reached/executed?

                holder.chip.getBackground().setAlpha(0);
                holder.subject.setText("No subject");
                holder.subject.setTypeface(null, Typeface.NORMAL);
                if (holder.preview != null)
                {
                    holder.preview.setText("No sender");
                    holder.preview.setTypeface(null, Typeface.NORMAL);
                    holder.preview.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
                }
                else
                {
                    holder.from.setText("No sender");
                    holder.from.setTypeface(null, Typeface.NORMAL);
                    holder.from.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
                }

                holder.date.setText("No date");

                //WARNING: Order of the next 2 lines matter
                holder.position = -1;
                holder.selected.setChecked(false);
                holder.groupPosition = -1;

                if (!mCheckboxes)
                {
                    holder.selected.setVisibility(View.GONE);
                }
                holder.flagged.setChecked(false);
            }

            holder.subject.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mFontSizes.getMessageListSubject());
            holder.date.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mFontSizes.getMessageListDate());

            if (mTouchView)
            {
                holder.preview.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mFontSizes.getMessageListSender());
            }
            else
            {
                holder.from.setTextSize(TypedValue.COMPLEX_UNIT_DIP, mFontSizes.getMessageListSender());
            }

            return view;
        }

        /**
         * Associate model data to view object.
         * @param groupPosition TODO
         * @param position
         *            The position of the item within the adapter's data set of
         *            the item whose view we want.
         * @param view
         *            Main view component to alter. Never <code>null</code>.
         * @param holder
         *            Convenience view holder - eases access to <tt>view</tt>
         *            child views. Never <code>null</code>.
         * @param message
         *            Never <code>null</code>.
         */
        private void bindView(int groupPosition, final int position, final View view,
                final MessageViewHolder holder, final MessageInfoHolder message)
        {
            // XXX TODO there has to be some way to walk our view hierarchy and get this
            holder.flagged.setTag((Integer)position);
            holder.flagged.setChecked(message.flagged);

            // So that the mSelectedCount is only incremented/decremented
            // when a user checks the checkbox (vs code)
            holder.position = -1;
            holder.selected.setChecked(message.selected);
            holder.groupPosition = -1;

            if (!mCheckboxes)
            {
                holder.selected.setVisibility(message.selected ? View.VISIBLE : View.GONE);
            }

            holder.chip.setBackgroundColor(message.message.getFolder().getAccount().getChipColor());
            holder.chip.getBackground().setAlpha(message.read ? 127 : 255);
            view.getBackground().setAlpha(message.downloaded ? 0 : 127);

            if ((message.subject == null) || "".equals(message.subject))
            {
                holder.subject.setText(getText(R.string.general_no_subject));
                holder.subject.setTypeface(null, message.read ? Typeface.ITALIC : Typeface.BOLD_ITALIC);
            }
            else
            {
                holder.subject.setText(message.subject);
                holder.subject.setTypeface(null,  message.read ? Typeface.NORMAL : Typeface.BOLD);
            }

            if (holder.preview != null)
            {
                /*
                 * In the touchable UI, we have previews. Otherwise, we
                 * have just a "from" line.
                 * Because text views can't wrap around each other(?) we
                 * compose a custom view containing the preview and the
                 * from.
                 */
                holder.preview.setText(message.sender + " " + message.preview,
                                       TextView.BufferType.SPANNABLE);
                Spannable str = (Spannable)holder.preview.getText();

                // Create our span sections, and assign a format to each.
                str.setSpan(
                    new TextAppearanceSpan(
                        null,
                        Typeface.BOLD,
                        -1,
                        holder.subject.getTextColors(),
                        holder.subject.getLinkTextColors()),
                    0,
                    message.sender.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
            else
            {
                holder.from.setText(message.sender);
                holder.from.setTypeface(null, message.read ? Typeface.NORMAL : Typeface.BOLD);
            }

            holder.date.setText(message.date);
            holder.subject.setCompoundDrawablesWithIntrinsicBounds(
                message.answered ? mAnsweredIcon : null, // left
                null, // top
                message.hasAttachments ? mAttachmentIcon : null, // right
                null); // bottom
            holder.position = position;
            holder.groupPosition = position;
        }

        public View getFooterView(int position, View convertView, ViewGroup parent)
        {
            if (footerView == null)
            {
                footerView = mInflater.inflate(R.layout.message_list_item_footer, parent, false);
                if (mQueryString != null)
                {
                    footerView.setVisibility(View.GONE);
                }
                footerView.setId(R.layout.message_list_item_footer);
                FooterViewHolder holder = new FooterViewHolder();
                holder.progress = (ProgressBar)footerView.findViewById(R.id.message_list_progress);
                holder.progress.setIndeterminate(true);
                holder.main = (TextView)footerView.findViewById(R.id.main_text);
                footerView.setTag(holder);
            }

            updateFooterView();

            return footerView;
        }


        /**
         * 
         */
        private void updateFooterView()
        {
            FooterViewHolder holder = (FooterViewHolder)footerView.getTag();

            if (mCurrentFolder != null && mAccount != null)
            {
                if (mCurrentFolder.loading || mGroupingInProgress)
                {
                    holder.main.setText(getString(R.string.status_loading_more));
                    holder.progress.setVisibility(ProgressBar.VISIBLE);
                }
                else
                {
                    if (mCurrentFolder.lastCheckFailed == false)
                    {
                        holder.main.setText(String.format(getString(R.string.load_more_messages_fmt), mAccount.getDisplayCount()));
                    }
                    else
                    {
                        holder.main.setText(getString(R.string.status_loading_more_failed));
                    }
                    holder.progress.setVisibility(ProgressBar.INVISIBLE);
                }
            }
            else
            {
                holder.progress.setVisibility(ProgressBar.INVISIBLE);
            }
        }

        @Override
        public boolean hasStableIds()
        {
            // remain consistent with underlying data and the UI state
            return false;
        }

        public boolean isItemSelectable(int position)
        {
            if (position < messages.size())
            {
                return true;
            }
            else
            {
                return false;
            }
        }


        @Override
        public int getGroupCount()
        {
            return mGroups.size() + NON_MESSAGE_ITEMS;
        }


        @Override
        public int getChildrenCount(int groupPosition)
        {
            if (groupPosition < mGroups.size())
            {
                return getGroup(groupPosition).getMessages().size();
            }
            // (fake) last element should have no children
            return 0;
        }


        @Override
        public MessageGroup<MessageInfoHolder> getGroup(int groupPosition)
        {
            if (groupPosition < mGroups.size())
            {
                return mGroups.get(groupPosition);
            }
            // (fake) last element isn't a group
            return null;
        }


        @Override
        public MessageInfoHolder getChild(int groupPosition, int childPosition)
        {
            final MessageGroup<MessageInfoHolder> group = getGroup(groupPosition);
            if (group == null)
            {
                return null;
            }
            return group.getMessages().get(childPosition).getTag();
        }


        @Override
        public long getGroupId(int groupPosition)
        {
            final MessageGroup<MessageInfoHolder> group = getGroup(groupPosition);
            if (group == null)
            {
                // the last group should match this case (as any other invalid
                // position)
                return -1;
            }
            // UI should stay consistent with the underlying data and needs to
            // keep track of the groups when list is updating (ie the selection/
            // expanded state should remain the "same" group if possible)

            if (mGroupLessMode)
            {
                // make sure we always get the same ID in groupless mode
                return 0;
            }

            return group.getId();
        }


        @Override
        public long getChildId(int groupPosition, int childPosition)
        {
            final MessageInfoHolder child = getChild(groupPosition, childPosition);
            if (child == null)
            {
                // unlikely to occur but still possible, returning fixed value
                return -1;
            }
            return ((LocalMessage) child.message).getId();
        }


        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView,
                ViewGroup parent)
        {
            // use of the 2.2 API for proper view recycling
            if (getGroupType(groupPosition) == 0)
            {
                return getMessageGroupView(groupPosition, isExpanded, convertView, parent);
            }
            else
            {
                return getFooterView(groupPosition, convertView, parent);
            }
        }

        @Override // automatically called from API level 8 for proper view recycling
        public int getGroupType(int groupPosition)
        {
            // must match the number of type of view returned by the above getGroupView method
            if (groupPosition < mGroups.size())
            {
                return 0;
            }
            else
            {
                return 1;
            }
        }

        @Override // automatically called from API level 8 for proper view recycling
        public int getGroupTypeCount()
        {
            // must match the above getGroupType method
            return 2;
        }

        private View getMessageGroupView(int groupPosition, boolean isExpanded, View convertView,
                ViewGroup parent)
        {
            final MessageGroup<MessageInfoHolder> group = getGroup(groupPosition);
            final View view;
            if (convertView == null || R.layout.message_list_group_header != convertView.getId())
            {
                // create new view
                view = mInflater.inflate(R.layout.message_list_group_header, parent, false);
                view.setId(R.layout.message_list_group_header);
            }
            else
            {
                // reuse view
                view = convertView;
            }
            final TextView subjectView = (TextView) view.findViewById(R.id.subject);
            final TextView countView = (TextView) view.findViewById(R.id.count);
            final TextView flagCountView = (TextView) view.findViewById(R.id.flagged_message_count);

            if (mGroupLessMode)
            {
                // TODO set localized text (or hide view?)
                subjectView.setText(group.getSubject());
            }
            else
            {
                subjectView.setText(group.getSubject());
            }
            subjectView
                    .setTextSize(TypedValue.COMPLEX_UNIT_DIP, mFontSizes.getMessageListSubject());

            int unreadCount = 0;
            int flagCount = 0;
            for (final MessageInfo<MessageInfoHolder> messageInfo : group.getMessages())
            {
                final MessageInfoHolder holder = messageInfo.getTag();
                if (!holder.read)
                {
                    unreadCount++;
                }
                if (holder.flagged)
                {
                    flagCount++;
                }
            }
            final int count = group.getMessages().size();
            if (unreadCount == 0)
            {
                // all read
                subjectView.setTypeface(null, Typeface.NORMAL);
                countView.setText(Integer.toString(count));
                countView.setTypeface(null, Typeface.NORMAL);
            }
            else
            {
                // at least 1 unread

                subjectView.setTypeface(null, Typeface.BOLD);

                if (unreadCount == count)
                {
                    // none read
                    countView.setText(Integer.toString(count));
                    countView.setTypeface(null, Typeface.BOLD);
                }
                else
                {
                    // mixed
                    final String unreadString = Integer.toString(unreadCount);
                    final String totalString = Integer.toString(count);

                    final Spannable spannableStringBuilder = new SpannableStringBuilder(
                            unreadString + '/' + totalString);
                    spannableStringBuilder.setSpan(new StyleSpan(Typeface.BOLD), 0,
                            unreadString.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                    countView.setTypeface(null, Typeface.NORMAL);
                    countView.setText(spannableStringBuilder);
                }
            }

            if (!mStars || flagCount == 0)
            {
                flagCountView.setVisibility(View.GONE);
            }
            else
            {
                flagCountView.setText(Integer.toString(flagCount));
                flagCountView.setVisibility(View.VISIBLE);
            }

            return view;
        }


        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                View convertView, ViewGroup parent)
        {
            return getItemView(groupPosition, childPosition, convertView, parent);
        }


        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition)
        {
            return true;
        }

        @Override
        public Object[] getSections()
        {
            // XXX setFastScrollEnabled HAS to be disabled then enabled back for this method to be called again!!!
            final int count = mListView.getCount();

            final String[] sections = new String[count];
            Arrays.fill(sections, 0, count, " ");
            return sections;
        }

        @Override
        public int getPositionForSection(int section)
        {
            // for some obscure reason (or I didn't read the documentation right), the returned
            // value must match a group position
            final long packedPosition = mListView.getExpandableListPosition(section);
            final int index = ExpandableListView.getPackedPositionGroup(packedPosition);
            return index;
        }

        @Override
        public int getSectionForPosition(int position)
        {
            // doesn't seem to be used by the Android framework?
            return position;
        }

    }

    public class MessageInfoHolder implements Comparable<MessageInfoHolder>
    {
        protected MessageGroup<MessageInfoHolder> group;
        public String subject;
        public String date;
        public Date compareDate;
        public String compareSubject;
        public String sender;
        public String compareCounterparty;
        public String preview;
        public String[] recipients;
        public boolean hasAttachments;
        public String uid;
        public boolean read;
        public boolean answered;
        public boolean flagged;
        public boolean downloaded;
        public boolean partially_downloaded;
        public Message message;
        public FolderInfoHolder folder;
        public boolean selected;

        // Empty constructor for comparison
        private MessageInfoHolder()
        {
            this.selected = false;
        }

        public MessageInfoHolder(Message m)
        {
            this();
            Account account = m.getFolder().getAccount();
            populate(m, new FolderInfoHolder(m.getFolder(), m.getFolder().getAccount()), account);
        }

        public MessageInfoHolder(Message m, FolderInfoHolder folder, Account account)
        {
            this();
            populate(m, folder, account);
        }

        public void populate(Message m, FolderInfoHolder folder, Account account)
        {
            try
            {
                LocalMessage message = (LocalMessage) m;
                Date date = message.getSentDate();
                this.compareDate = message.getSentDate();
                if (this.compareDate == null)
                {
                    this.compareDate = message.getInternalDate();
                }

                this.folder = folder;

                if (Utility.isDateToday(date))
                {
                    this.date = getTimeFormat().format(date);
                }
                else
                {
                    this.date = getDateFormat().format(date);
                }

                this.hasAttachments = message.getAttachmentCount() > 0;

                this.read = message.isSet(Flag.SEEN);
                this.answered = message.isSet(Flag.ANSWERED);
                this.flagged = message.isSet(Flag.FLAGGED);
                this.downloaded = message.isSet(Flag.X_DOWNLOADED_FULL);
                this.partially_downloaded = message.isSet(Flag.X_DOWNLOADED_PARTIAL);

                Address[] addrs = message.getFrom();

                if (addrs.length > 0 &&  account.isAnIdentity(addrs[0]))
                {
                    this.compareCounterparty = Address.toFriendly(message .getRecipients(RecipientType.TO));
                    this.sender = String.format(getString(R.string.message_list_to_fmt), this.compareCounterparty);
                }
                else
                {
                    this.sender = Address.toFriendly(addrs);
                    this.compareCounterparty = this.sender;
                }

                this.subject = message.getSubject();

                this.uid = message.getUid();
                this.message = m;
                this.preview = message.getPreview();
            }
            catch (MessagingException me)
            {
                if (Config.LOGV)
                {
                    Log.v(K9.LOG_TAG, "Unable to load message info", me);
                }
            }
        }

        @Override
        public boolean equals(Object o)
        {
            if (o instanceof MessageInfoHolder == false)
            {
                return false;
            }
            MessageInfoHolder other = (MessageInfoHolder)o;
            return message.equals(other.message);
        }

        @Override
        public int hashCode()
        {
            return uid.hashCode();
        }

        @Override
        public int compareTo(MessageInfoHolder o)
        {
            int ascender = (sortAscending ? 1 : -1);
            int comparison = 0;

            if (sortType == SORT_TYPE.SORT_SUBJECT)
            {
                if (compareSubject == null)
                {
                    compareSubject = stripPrefixes(subject).toLowerCase();
                }

                if (o.compareSubject == null)
                {
                    o.compareSubject = stripPrefixes(o.subject).toLowerCase();
                }

                comparison = this.compareSubject.compareTo(o.compareSubject);
            }
            else if (sortType == SORT_TYPE.SORT_SENDER)
            {
                comparison = this.compareCounterparty.toLowerCase().compareTo(o.compareCounterparty.toLowerCase());
            }
            else if (sortType == SORT_TYPE.SORT_FLAGGED)
            {
                comparison = (this.flagged ? 0 : 1) - (o.flagged ? 0 : 1);
            }
            else if (sortType == SORT_TYPE.SORT_UNREAD)
            {
                comparison = (this.read ? 1 : 0) - (o.read ? 1 : 0);
            }
            else if (sortType == SORT_TYPE.SORT_ATTACHMENT)
            {
                comparison = (this.hasAttachments ? 0 : 1) - (o.hasAttachments ? 0 : 1);
            }

            if (comparison != 0)
            {
                return comparison * ascender;
            }

            int dateAscender = (sortDateAscending ? 1 : -1);

            return this.compareDate.compareTo(o.compareDate) * dateAscender;
        }

        Pattern pattern = null;
        String patternString = "^ *(re|aw|fw|fwd): *";
        private String stripPrefixes(String in)
        {
            synchronized (patternString)
            {
                if (pattern == null)
                {
                    pattern = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
                }
            }

            Matcher matcher = pattern.matcher(in);

            int lastPrefix = -1;

            while (matcher.find())
            {
                lastPrefix = matcher.end();
            }

            if (lastPrefix > -1 && lastPrefix < in.length() - 1)
            {
                return in.substring(lastPrefix);
            }
            else
            {
                return in;
            }
        }
    }

    class MessageViewHolder
            implements OnCheckedChangeListener
    {
        public TextView subject;
        public TextView preview;
        public TextView from;
        public TextView time;
        public TextView date;
        public CheckBox flagged;
        public View chip;
        public CheckBox selected;
        public int position = -1;
        private int groupPosition = -1;

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
        {
            if (position!=-1)
            {
                MessageInfoHolder message = mAdapter.getChild(position, groupPosition);
                if (message.selected!=isChecked)
                {
                    if (isChecked)
                    {
                        mSelectedCount++;
                    }
                    else if (mSelectedCount > 0)
                    {
                        mSelectedCount--;
                    }

                    // We must set the flag before showing the buttons as the
                    // buttons text depends on what is selected.
                    message.selected = isChecked;
                    if (!mCheckboxes)
                    {
                        if (isChecked == true)
                        {
                            selected.setVisibility(View.VISIBLE);
                        }
                        else
                        {
                            selected.setVisibility(View.GONE);
                        }
                    }
                    toggleBatchButtons();
                }
            }
        }
    }

    private void hideBatchButtons()
    {
        //TODO: Fade out animation
        mBatchButtonArea.setVisibility(View.GONE);
    }

    private void showBatchButtons()
    {
        //TODO: Fade in animation
        mBatchButtonArea.setVisibility(View.VISIBLE);
    }

    private void toggleBatchButtons()
    {
        if (mSelectedCount < 0)
        {
            mSelectedCount = 0;
        }

        int readButtonIconId;
        int flagButtonIconId;

        if (mSelectedCount==0)
        {
            readButtonIconId = R.drawable.ic_button_mark_read;
            flagButtonIconId = R.drawable.ic_button_flag;
            hideBatchButtons();
        }
        else
        {
            boolean newReadState = computeBatchDirection(false);
            if (newReadState)
            {
                readButtonIconId = R.drawable.ic_button_mark_read;
            }
            else
            {
                readButtonIconId = R.drawable.ic_button_mark_unread;
            }
            boolean newFlagState = computeBatchDirection(true);
            if (newFlagState)
            {
                flagButtonIconId = R.drawable.ic_button_flag;
            }
            else
            {
                flagButtonIconId = R.drawable.ic_button_unflag;
            }
            showBatchButtons();
        }

        mBatchReadButton.setImageResource(readButtonIconId);
        mBatchFlagButton.setImageResource(flagButtonIconId);
    }

    class FooterViewHolder
    {
        public ProgressBar progress;
        public TextView main;
    }

    public class FolderInfoHolder
    {
        public String name;
        public String displayName;
        public boolean loading;
        public boolean lastCheckFailed;
        public Folder folder;

        /**
         * Outbox is handled differently from any other folder.
         */
        public boolean outbox;

        public FolderInfoHolder(Folder folder, Account account)
        {
            populate(folder, account);
        }

        public void populate(Folder folder, Account account)
        {
            this.folder = folder;
            this.name = folder.getName();

            if (this.name.equalsIgnoreCase(K9.INBOX))
            {
                this.displayName = getString(R.string.special_mailbox_name_inbox);
            }
            else
            {
                this.displayName = this.name;
            }

            if (this.name.equals(account.getOutboxFolderName()))
            {
                this.displayName = String.format(getString(R.string.special_mailbox_name_outbox_fmt), this.name);
                this.outbox = true;
            }

            if (this.name.equals(account.getDraftsFolderName()))
            {
                this.displayName = String.format(getString(R.string.special_mailbox_name_drafts_fmt), this.name);
            }

            if (this.name.equals(account.getTrashFolderName()))
            {
                this.displayName = String.format(getString(R.string.special_mailbox_name_trash_fmt), this.name);
            }

            if (this.name.equals(account.getSentFolderName()))
            {
                this.displayName = String.format(getString(R.string.special_mailbox_name_sent_fmt), this.name);
            }

            if (this.name.equals(account.getArchiveFolderName()))
            {
                this.displayName = String.format(getString(R.string.special_mailbox_name_archive_fmt), this.name);
            }

            if (this.name.equals(account.getSpamFolderName()))
            {
                this.displayName = String.format(getString(R.string.special_mailbox_name_spam_fmt), this.name);
            }
        }
    }

    private boolean computeBatchDirection(boolean flagged)
    {
        boolean newState = false;

        synchronized (mAdapter.messages)
        {
            for (MessageInfoHolder holder : mAdapter.messages)
            {
                if (holder.selected)
                {
                    if (flagged)
                    {
                        if (!holder.flagged)
                        {
                            newState = true;
                            break;
                        }
                    }
                    else
                    {
                        if (!holder.read)
                        {
                            newState = true;
                            break;
                        }
                    }
                }
            }
        }
        return newState;
    }

    private boolean anySelected()
    {
        synchronized (mAdapter.messages)
        {
            for (MessageInfoHolder holder : mAdapter.messages)
            {
                if (holder.selected)
                {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onClick(View v)
    {
        boolean newState = false;
        List<Message> messageList = new ArrayList<Message>();
        List<MessageInfoHolder> removeHolderList = new ArrayList<MessageInfoHolder>();

        if (v == mBatchDoneButton)
        {
            setAllSelected(false);
            return;
        }

        if (v == mBatchFlagButton)
        {
            newState = computeBatchDirection(true);
        }
        else
        {
            newState = computeBatchDirection(false);
        }

        synchronized (mAdapter.messages)
        {
            for (MessageInfoHolder holder : mAdapter.messages)
            {
                if (holder.selected)
                {
                    if (v == mBatchDeleteButton)
                    {
                        removeHolderList.add(holder);
                    }
                    else if (v == mBatchFlagButton)
                    {
                        holder.flagged = newState;
                    }
                    else if (v == mBatchReadButton)
                    {
                        holder.read = newState;
                    }
                    messageList.add(holder.message);
                }
            }
        }
        mAdapter.removeMessages(removeHolderList);

        if (!messageList.isEmpty())
        {
            if (v == mBatchDeleteButton)
            {
                mController.deleteMessages(messageList.toArray(EMPTY_MESSAGE_ARRAY), null);
                mSelectedCount = 0;
                toggleBatchButtons();
            }
            else
            {
                mController.setFlag(messageList.toArray(EMPTY_MESSAGE_ARRAY), (v == mBatchReadButton ? Flag.SEEN : Flag.FLAGGED), newState);
            }
        }
        else
        {
            // Should not happen
            Toast.makeText(this, R.string.no_message_seletected_toast, Toast.LENGTH_SHORT).show();
        }
        mHandler.sortMessages();
    }
    
    private void setAllSelected(boolean isSelected)
    {
        mSelectedCount = 0;
        synchronized (mAdapter.messages)
        {
            for (MessageInfoHolder holder : mAdapter.messages)
            {
                holder.selected = isSelected;
                mSelectedCount += (isSelected ? 1 : 0);
            }
        }
        mAdapter.notifyDataSetChanged();
        toggleBatchButtons();
    }

    private void setSelected(final List<MessageInfoHolder> holders, final boolean newState)
    {
        for (final MessageInfoHolder holder : holders)
        {
            if (holder.selected != newState)
            {
                holder.selected = newState;
                mSelectedCount += (newState ? 1 : -1);
            }
        }
        mAdapter.notifyDataSetChanged();
        toggleBatchButtons();
    }

    private void flagSelected(Flag flag, boolean newState)
    {
        List<Message> messageList = new ArrayList<Message>();
        synchronized (mAdapter.messages)
        {
            for (MessageInfoHolder holder : mAdapter.messages)
            {
                if (holder.selected)
                {
                    messageList.add(holder.message);
                    if (flag == Flag.SEEN)
                    {
                        holder.read = newState;
                    }
                    else if (flag == Flag.FLAGGED)
                    {
                        holder.flagged = newState;
                    }
                }
            }
        }
        mController.setFlag(messageList.toArray(EMPTY_MESSAGE_ARRAY), flag, newState);
        mHandler.sortMessages();
    }

    private void deleteSelected()
    {
        List<Message> messageList = new ArrayList<Message>();
        List<MessageInfoHolder> removeHolderList = new ArrayList<MessageInfoHolder>();
        synchronized (mAdapter.messages)
        {
            for (MessageInfoHolder holder : mAdapter.messages)
            {
                if (holder.selected)
                {
                    removeHolderList.add(holder);
                    messageList.add(holder.message);
                }
            }
        }
        mAdapter.removeMessages(removeHolderList);

        mController.deleteMessages(messageList.toArray(EMPTY_MESSAGE_ARRAY), null);
        mSelectedCount = 0;
        toggleBatchButtons();
    }

    private void onMoveBatch()
    {
        if (mController.isMoveCapable(mAccount) == false)
        {
            return;
        }

        synchronized (mAdapter.messages)
        {
            for (MessageInfoHolder holder : mAdapter.messages)
            {
                if (holder.selected)
                {
                    Message message = holder.message;
                    if (mController.isMoveCapable(message) == false)
                    {
                        Toast toast = Toast.makeText(this,
                                                     R.string.move_copy_cannot_copy_unsynced_message, Toast.LENGTH_LONG);
                        toast.show();
                        return;
                    }
                }
            }
        }

        final Folder folder = mCurrentFolder.folder;
        final Intent intent = new Intent(this, ChooseFolder.class);
        intent.putExtra(ChooseFolder.EXTRA_ACCOUNT, mAccount.getUuid());
        intent.putExtra(ChooseFolder.EXTRA_CUR_FOLDER, folder.getName());
        intent.putExtra(ChooseFolder.EXTRA_SEL_FOLDER, folder.getAccount().getLastSelectedFolderName());
        startActivityForResult(intent, ACTIVITY_CHOOSE_FOLDER_MOVE_BATCH);
    }

    private void onMoveChosenBatch(String folderName)
    {
        if (mController.isMoveCapable(mAccount) == false)
        {
            return;
        }
        List<Message> messageList = new ArrayList<Message>();

        List<MessageInfoHolder> removeHolderList = new ArrayList<MessageInfoHolder>();
        synchronized (mAdapter.messages)
        {
            for (MessageInfoHolder holder : mAdapter.messages)
            {
                if (holder.selected)
                {
                    Message message = holder.message;
                    if (mController.isMoveCapable(message) == false)
                    {
                        Toast toast = Toast.makeText(this,
                                                     R.string.move_copy_cannot_copy_unsynced_message, Toast.LENGTH_LONG);
                        toast.show();
                        return;
                    }
                    messageList.add(holder.message);
                    removeHolderList.add(holder);
                }
            }
        }
        mAdapter.removeMessages(removeHolderList);

        mController.moveMessages(mAccount, mCurrentFolder.name, messageList.toArray(EMPTY_MESSAGE_ARRAY), folderName, null);
        mSelectedCount = 0;
        toggleBatchButtons();
    }

    private void onArchiveBatch()
    {
        if (mController.isMoveCapable(mAccount) == false)
        {
            return;
        }

        synchronized (mAdapter.messages)
        {
            for (MessageInfoHolder holder : mAdapter.messages)
            {
                if (holder.selected)
                {
                    Message message = holder.message;
                    if (mController.isMoveCapable(message) == false)
                    {
                        Toast toast = Toast.makeText(this, R.string.move_copy_cannot_copy_unsynced_message, Toast.LENGTH_LONG);
                        toast.show();
                        return;
                    }
                }
            }
        }

        String folderName = mAccount.getArchiveFolderName();
        if (K9.FOLDER_NONE.equalsIgnoreCase(folderName))
        {
            return;
        }
        onMoveChosenBatch(folderName);
    }

    private void onSpamBatch()
    {
        if (mController.isMoveCapable(mAccount) == false)
        {
            return;
        }

        synchronized (mAdapter.messages)
        {
            for (MessageInfoHolder holder : mAdapter.messages)
            {
                if (holder.selected)
                {
                    Message message = holder.message;
                    if (mController.isMoveCapable(message) == false)
                    {
                        Toast toast = Toast.makeText(this, R.string.move_copy_cannot_copy_unsynced_message, Toast.LENGTH_LONG);
                        toast.show();
                        return;
                    }
                }
            }
        }

        String folderName = mAccount.getSpamFolderName();
        if (K9.FOLDER_NONE.equalsIgnoreCase(folderName))
        {
            return;
        }
        onMoveChosenBatch(folderName);
    }

    private void onCopyBatch()
    {
        if (mController.isCopyCapable(mAccount) == false)
        {
            return;
        }

        synchronized (mAdapter.messages)
        {
            for (MessageInfoHolder holder : mAdapter.messages)
            {
                if (holder.selected)
                {
                    Message message = holder.message;
                    if (mController.isCopyCapable(message) == false)
                    {
                        Toast toast = Toast.makeText(this,
                                                     R.string.move_copy_cannot_copy_unsynced_message, Toast.LENGTH_LONG);
                        toast.show();
                        return;
                    }
                }
            }
        }

        final Folder folder = mCurrentFolder.folder;
        final Intent intent = new Intent(this, ChooseFolder.class);
        intent.putExtra(ChooseFolder.EXTRA_ACCOUNT, mAccount.getUuid());
        intent.putExtra(ChooseFolder.EXTRA_CUR_FOLDER, folder.getName());
        intent.putExtra(ChooseFolder.EXTRA_SEL_FOLDER, folder.getAccount().getLastSelectedFolderName());
        startActivityForResult(intent, ACTIVITY_CHOOSE_FOLDER_COPY_BATCH);
    }

    private void onCopyChosenBatch(String folderName)
    {
        if (mController.isCopyCapable(mAccount) == false)
        {
            return;
        }

        List<Message> messageList = new ArrayList<Message>();
        synchronized (mAdapter.messages)
        {
            for (MessageInfoHolder holder : mAdapter.messages)
            {
                if (holder.selected)
                {
                    Message message = holder.message;
                    if (mController.isCopyCapable(message) == false)
                    {
                        Toast toast = Toast.makeText(this,
                                                     R.string.move_copy_cannot_copy_unsynced_message, Toast.LENGTH_LONG);
                        toast.show();
                        return;
                    }
                    messageList.add(holder.message);
                }
            }
        }
        mController.copyMessages(mAccount, mCurrentFolder.name, messageList.toArray(EMPTY_MESSAGE_ARRAY), folderName, null);
    }
}
