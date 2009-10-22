/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.phone;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.CallerInfoAsyncQuery;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;

import android.content.ContentUris;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.pim.ContactsAsyncHelper;
import android.provider.Contacts.People;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import android.provider.Contacts.Organizations;

/**
 * "Call card" UI element: the in-call screen contains a tiled layout of call
 * cards, each representing the state of a current "call" (ie. an active call,
 * a call on hold, or an incoming call.)
 */
public class CallCard extends FrameLayout
        implements CallTime.OnTickListener, CallerInfoAsyncQuery.OnQueryCompleteListener,
                ContactsAsyncHelper.OnImageLoadCompleteListener{
    private static final String LOG_TAG = "CallCard";
    private static final boolean DBG = (PhoneApp.DBG_LEVEL >= 2);

    /**
     * Reference to the InCallScreen activity that owns us.  This may be
     * null if we haven't been initialized yet *or* after the InCallScreen
     * activity has been destroyed.
     */
    private InCallScreen mInCallScreen;

    // Phone app instance
    private PhoneApp mApplication;

    // Top-level subviews of the CallCard
    private ViewGroup mMainCallCard;
    private ViewGroup mOtherCallOngoingInfoArea;
    private ViewGroup mOtherCallOnHoldInfoArea;

    // "Upper" and "lower" title widgets
    private TextView mUpperTitle;
    private ViewGroup mLowerTitleViewGroup;
    private TextView mLowerTitle;
    private ImageView mLowerTitleIcon;
    private TextView mElapsedTime;

    // Text colors, used with the lower title and "other call" info areas
    private int mTextColorConnected;
    private int mTextColorConnectedBluetooth;
    private int mTextColorEnded;
    private int mTextColorOnHold;

    private ImageView mPhoto;
    private TextView mName;
    private TextView mPhoneNumber;
    private TextView mLabel;

    // "Other call" info area
    private ImageView mOtherCallOngoingIcon;
    private TextView mOtherCallOngoingName;
    private TextView mOtherCallOngoingStatus;
    private TextView mOtherCallOnHoldName;
    private TextView mOtherCallOnHoldStatus;

    // Menu button hint
    private TextView mMenuButtonHint;

    private CallTime mCallTime;

    // Track the state for the photo.
    private ContactsAsyncHelper.ImageTracker mPhotoTracker;

    // A few hardwired constants used in our screen layout.
    // TODO: These should all really come from resources, but that's
    // nontrivial; see the javadoc for the ConfigurationHelper class.
    // For now, let's at least keep them all here in one place
    // rather than sprinkled througout this file.
    //
    static final int MAIN_CALLCARD_MIN_HEIGHT_LANDSCAPE = 200;
    static final int CALLCARD_SIDE_MARGIN_LANDSCAPE = 50;
    static final float TITLE_TEXT_SIZE_LANDSCAPE = 22F;  // scaled pixels

// add by cytown
private CallFeaturesSetting mSettings;

    public CallCard(Context context, AttributeSet attrs) {
        super(context, attrs);

        if (DBG) log("CallCard constructor...");
        if (DBG) log("- this = " + this);
        if (DBG) log("- context " + context + ", attrs " + attrs);

        // Inflate the contents of this CallCard, and add it (to ourself) as a child.
        LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(
                R.layout.call_card,  // resource
                this,                // root
                true);

        mApplication = PhoneApp.getInstance();

// add by cytown
mSettings = CallFeaturesSetting.getInstance(android.preference.PreferenceManager.getDefaultSharedPreferences(context));

        mCallTime = new CallTime(this);

        // create a new object to track the state for the photo.
        mPhotoTracker = new ContactsAsyncHelper.ImageTracker();
    }

    void setInCallScreenInstance(InCallScreen inCallScreen) {
        mInCallScreen = inCallScreen;
    }

    public void onTickForCallTimeElapsed(long timeElapsed) {
        // While a call is in progress, update the elapsed time shown
        // onscreen.
        updateElapsedTimeWidget(timeElapsed);
    }

    /* package */
    void stopTimer() {
        mCallTime.cancelTimer();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        if (DBG) log("CallCard onFinishInflate(this = " + this + ")...");

        mMainCallCard = (ViewGroup) findViewById(R.id.mainCallCard);
        mOtherCallOngoingInfoArea = (ViewGroup) findViewById(R.id.otherCallOngoingInfoArea);
        mOtherCallOnHoldInfoArea = (ViewGroup) findViewById(R.id.otherCallOnHoldInfoArea);

        // "Upper" and "lower" title widgets
        mUpperTitle = (TextView) findViewById(R.id.upperTitle);
        mLowerTitleViewGroup = (ViewGroup) findViewById(R.id.lowerTitleViewGroup);
        mLowerTitle = (TextView) findViewById(R.id.lowerTitle);
        mLowerTitleIcon = (ImageView) findViewById(R.id.lowerTitleIcon);
        mElapsedTime = (TextView) findViewById(R.id.elapsedTime);

        // Text colors
        mTextColorConnected = getResources().getColor(R.color.incall_textConnected);
        mTextColorConnectedBluetooth =
                getResources().getColor(R.color.incall_textConnectedBluetooth);
        mTextColorEnded = getResources().getColor(R.color.incall_textEnded);
        mTextColorOnHold = getResources().getColor(R.color.incall_textOnHold);

        // "Caller info" area, including photo / name / phone numbers / etc
        mPhoto = (ImageView) findViewById(R.id.photo);
        mName = (TextView) findViewById(R.id.name);
        mPhoneNumber = (TextView) findViewById(R.id.phoneNumber);
        mLabel = (TextView) findViewById(R.id.label);

        // "Other call" info area
        mOtherCallOngoingIcon = (ImageView) findViewById(R.id.otherCallOngoingIcon);
        mOtherCallOngoingName = (TextView) findViewById(R.id.otherCallOngoingName);
        mOtherCallOngoingStatus = (TextView) findViewById(R.id.otherCallOngoingStatus);
        mOtherCallOnHoldName = (TextView) findViewById(R.id.otherCallOnHoldName);
        mOtherCallOnHoldStatus = (TextView) findViewById(R.id.otherCallOnHoldStatus);

        // Menu Button hint
        mMenuButtonHint = (TextView) findViewById(R.id.menuButtonHint);
    }

    /**
     * Updates the state of all UI elements on the CallCard, based on the
     * current state of the phone.
     */
    void updateState(Phone phone) {
        if (DBG) log("updateState(" + phone + ")...");

        // Update some internal state based on the current state of the phone.
        // TODO: This code, and updateForegroundCall() / updateRingingCall(),
        // can probably still be simplified some more.

        Phone.State state = phone.getState();  // IDLE, RINGING, or OFFHOOK
        if (state == Phone.State.RINGING) {
            // A phone call is ringing *or* call waiting
            // (ie. another call may also be active as well.)
            updateRingingCall(phone);
        } else if (state == Phone.State.OFFHOOK) {
            // The phone is off hook. At least one call exists that is
            // dialing, active, or holding, and no calls are ringing or waiting.
            updateForegroundCall(phone);
        } else {
            // The phone state is IDLE!
            //
            // The most common reason for this is if a call just
            // ended: the phone will be idle, but we *will* still
            // have a call in the DISCONNECTED state:
            Call fgCall = phone.getForegroundCall();
            Call bgCall = phone.getBackgroundCall();
            if ((fgCall.getState() == Call.State.DISCONNECTED)
                || (bgCall.getState() == Call.State.DISCONNECTED)) {
                // In this case, we want the main CallCard to display
                // the "Call ended" state.  The normal "foreground call"
                // code path handles that.
                updateForegroundCall(phone);
            } else {
                // We don't have any DISCONNECTED calls, which means
                // that the phone is *truly* idle.
                //
                // It's very rare to be on the InCallScreen at all in this
                // state, but it can happen in some cases:
                // - A stray onPhoneStateChanged() event came in to the
                //   InCallScreen *after* it was dismissed.
                // - We're allowed to be on the InCallScreen because
                //   an MMI or USSD is running, but there's no actual "call"
                //   to display.
                // - We're displaying an error dialog to the user
                //   (explaining why the call failed), so we need to stay on
                //   the InCallScreen so that the dialog will be visible.
                //
                // In these cases, put the callcard into a sane but "blank" state:
                updateNoCall(phone);
            }
        }
    }

    /**
     * Updates the UI for the state where the phone is in use, but not ringing.
     */
    private void updateForegroundCall(Phone phone) {
        if (DBG) log("updateForegroundCall()...");

        Call fgCall = phone.getForegroundCall();
        Call bgCall = phone.getBackgroundCall();

        if (fgCall.isIdle() && !fgCall.hasConnections()) {
            if (DBG) log("updateForegroundCall: no active call, show holding call");
            // TODO: make sure this case agrees with the latest UI spec.

            // Display the background call in the main info area of the
            // CallCard, since there is no foreground call.  Note that
            // displayMainCallStatus() will notice if the call we passed in is on
            // hold, and display the "on hold" indication.
            fgCall = bgCall;

            // And be sure to not display anything in the "on hold" box.
            bgCall = null;
        }

        displayMainCallStatus(phone, fgCall);

        if (phone.getPhoneName().equals("CDMA")) {
            if (mApplication.cdmaPhoneCallState.getCurrentCallState()
                    == CdmaPhoneCallState.PhoneCallState.THRWAY_ACTIVE) {
                displayOnHoldCallStatus(phone, fgCall);
            } else {
                //This is required so that even if a background call is not present
                // we need to clean up the background call area.
                displayOnHoldCallStatus(phone, bgCall);
            }
        } else {
            displayOnHoldCallStatus(phone, bgCall);
        }

        displayOngoingCallStatus(phone, null);
    }

    /**
     * Updates the UI for the "generic call" state, where the phone is in
     * use but we don't know any specific details about the state of the
     * call (like who you're talking to, or how many lines are in use.)
     */
    private void updateGenericCall(Phone phone) {
        if (DBG) log("updateForegroundCall()...");

        Call fgCall = phone.getForegroundCall();

        // Display the special "generic" state in the main call area:
        displayMainCallGeneric(phone, fgCall);

        // And hide the "other call" info areas:
        displayOnHoldCallStatus(phone, null);
        displayOngoingCallStatus(phone, null);
    }

    /**
     * Updates the UI for the state where an incoming call is ringing (or
     * call waiting), regardless of whether the phone's already offhook.
     */
    private void updateRingingCall(Phone phone) {
        if (DBG) log("updateRingingCall()...");

        Call ringingCall = phone.getRingingCall();
        Call fgCall = phone.getForegroundCall();
        Call bgCall = phone.getBackgroundCall();

        displayMainCallStatus(phone, ringingCall);
        displayOnHoldCallStatus(phone, bgCall);
        displayOngoingCallStatus(phone, fgCall);
    }

    /**
     * Updates the UI for the state where the phone is not in use.
     * This is analogous to updateForegroundCall() and updateRingingCall(),
     * but for the (uncommon) case where the phone is
     * totally idle.  (See comments in updateState() above.)
     *
     * This puts the callcard into a sane but "blank" state.
     */
    private void updateNoCall(Phone phone) {
        if (DBG) log("updateNoCall()...");

        displayMainCallStatus(phone, null);
        displayOnHoldCallStatus(phone, null);
        displayOngoingCallStatus(phone, null);
    }

    /**
     * Updates the main block of caller info on the CallCard
     * (ie. the stuff in the mainCallCard block) based on the specified Call.
     */
    private void displayMainCallStatus(Phone phone, Call call) {
        if (DBG) log("displayMainCallStatus(phone " + phone
                     + ", call " + call + ")...");

        if (call == null) {
            // There's no call to display, presumably because the phone is idle.
            mMainCallCard.setVisibility(View.GONE);
            return;
        }
        mMainCallCard.setVisibility(View.VISIBLE);

        Call.State state = call.getState();
        if (DBG) log("  - call.state: " + call.getState());

        int callCardBackgroundResid = 0;

        // Background frame resources are different between portrait/landscape.
        // TODO: Don't do this manually.  Instead let the resource system do
        // it: just move the *_land assets over to the res/drawable-land
        // directory (but with the same filename as the corresponding
        // portrait asset.)
        boolean landscapeMode = InCallScreen.ConfigurationHelper.isLandscape();

        // Background images are also different if Bluetooth is active.
        final boolean bluetoothActive = mApplication.showBluetoothIndication();

        switch (state) {
            case ACTIVE:
                if (bluetoothActive) {
                    callCardBackgroundResid =
                            landscapeMode ? R.drawable.incall_frame_bluetooth_tall_land
                            : R.drawable.incall_frame_bluetooth_tall_port;
                } else {
                    callCardBackgroundResid =
                            landscapeMode ? R.drawable.incall_frame_connected_tall_land
                            : R.drawable.incall_frame_connected_tall_port;
                }

                // update timer field
                if (DBG) log("displayMainCallStatus: start periodicUpdateTimer");
                mCallTime.setActiveCallMode(call);
                mCallTime.reset();
                mCallTime.periodicUpdateTimer();

                break;

            case HOLDING:
                callCardBackgroundResid =
                        landscapeMode ? R.drawable.incall_frame_hold_tall_land
                        : R.drawable.incall_frame_hold_tall_port;

                // update timer field
                mCallTime.cancelTimer();

                break;

            case DISCONNECTED:
                callCardBackgroundResid =
                        landscapeMode ? R.drawable.incall_frame_ended_tall_land
                        : R.drawable.incall_frame_ended_tall_port;

                // Stop getting timer ticks from this call
                mCallTime.cancelTimer();

                break;

            case DIALING:
            case ALERTING:
                if (bluetoothActive) {
                    callCardBackgroundResid =
                            landscapeMode ? R.drawable.incall_frame_bluetooth_tall_land
                            : R.drawable.incall_frame_bluetooth_tall_port;
                } else {
                    callCardBackgroundResid =
                            landscapeMode ? R.drawable.incall_frame_normal_tall_land
                            : R.drawable.incall_frame_normal_tall_port;
                }

                // Stop getting timer ticks from a previous call
                mCallTime.cancelTimer();

                break;

            case INCOMING:
            case WAITING:
                if (bluetoothActive) {
                    callCardBackgroundResid =
                            landscapeMode ? R.drawable.incall_frame_bluetooth_tall_land
                            : R.drawable.incall_frame_bluetooth_tall_port;
                } else {
                    callCardBackgroundResid =
                            landscapeMode ? R.drawable.incall_frame_normal_tall_land
                            : R.drawable.incall_frame_normal_tall_port;
                }

                // Stop getting timer ticks from a previous call
                mCallTime.cancelTimer();

                break;

            case IDLE:
                // The "main CallCard" should never be trying to display
                // an idle call!  In updateState(), if the phone is idle,
                // we call updateNoCall(), which means that we shouldn't
                // have passed a call into this method at all.
                Log.w(LOG_TAG, "displayMainCallStatus: IDLE call in the main call card!");

                // (It is possible, though, that we had a valid call which
                // became idle *after* the check in updateState() but
                // before we get here...  So continue the best we can,
                // with whatever (stale) info we can get from the
                // passed-in Call object.)

                break;

            default:
                Log.w(LOG_TAG, "displayMainCallStatus: unexpected call state: " + state);
                break;
        }

        // Set the background frame color based on the state of the call.
        setMainCallCardBackgroundResource(callCardBackgroundResid);
        // (Text colors are set in updateCardTitleWidgets().)

        updateCardTitleWidgets(phone, call);

        if (PhoneUtils.isConferenceCall(call)) {
            // Update onscreen info for a conference call.
            updateDisplayForConference();
        } else {
            // Update onscreen info for a regular call (which presumably
            // has only one connection.)
            Connection conn = null;
            if (phone.getPhoneName().equals("CDMA")) {
                conn = call.getLatestConnection();
            } else { // GSM.
                conn = call.getEarliestConnection();
            }

            if (conn == null) {
                if (DBG) log("displayMainCallStatus: connection is null, using default values.");
                // if the connection is null, we run through the behaviour
                // we had in the past, which breaks down into trivial steps
                // with the current implementation of getCallerInfo and
                // updateDisplayForPerson.
                CallerInfo info = PhoneUtils.getCallerInfo(getContext(), null /* conn */);
                updateDisplayForPerson(info, Connection.PRESENTATION_ALLOWED, false, call);
            } else {
                if (DBG) log("  - CONN: " + conn + ", state = " + conn.getState());
                int presentation = conn.getNumberPresentation();

                // make sure that we only make a new query when the current
                // callerinfo differs from what we've been requested to display.
                boolean runQuery = true;
                Object o = conn.getUserData();
                if (o instanceof PhoneUtils.CallerInfoToken) {
                    runQuery = mPhotoTracker.isDifferentImageRequest(
                            ((PhoneUtils.CallerInfoToken) o).currentInfo);
                } else {
                    runQuery = mPhotoTracker.isDifferentImageRequest(conn);
                }

                // Adding a check to see if the update was caused due to a Phone number update
                // or CNAP update. If so then we need to start a new query
                if (phone.getPhoneName().equals("CDMA")) {
                    Object obj = conn.getUserData();
                    String updatedNumber = conn.getAddress();
                    String updatedCnapName = conn.getCnapName();
                    CallerInfo info = null;
                    if (obj instanceof PhoneUtils.CallerInfoToken) {
                        info = ((PhoneUtils.CallerInfoToken) o).currentInfo;
                    } else if (o instanceof CallerInfo) {
                        info = (CallerInfo) o;
                    }

                    if (info != null) {
                        if (updatedNumber != null && !updatedNumber.equals(info.phoneNumber)) {
                            if (DBG) log("- displayMainCallStatus: updatedNumber = "
                                    + updatedNumber);
                            runQuery = true;
                        }
                        if (updatedCnapName != null && !updatedCnapName.equals(info.cnapName)) {
                            if (DBG) log("- displayMainCallStatus: updatedCnapName = "
                                    + updatedCnapName);
                            runQuery = true;
                        }
                    }
                }

                if (runQuery) {
                    if (DBG) log("- displayMainCallStatus: starting CallerInfo query...");
                    PhoneUtils.CallerInfoToken info =
                            PhoneUtils.startGetCallerInfo(getContext(), conn, this, call);
                    updateDisplayForPerson(info.currentInfo, presentation, !info.isFinal, call);
                } else {
                    // No need to fire off a new query.  We do still need
                    // to update the display, though (since we might have
                    // previously been in the "conference call" state.)
                    if (DBG) log("- displayMainCallStatus: using data we already have...");
                    if (o instanceof CallerInfo) {
                        CallerInfo ci = (CallerInfo) o;
                        // Update CNAP information if Phone state change occurred
                        ci.cnapName = conn.getCnapName();
                        ci.numberPresentation = conn.getNumberPresentation();
                        ci.namePresentation = conn.getCnapNamePresentation();
                        if (DBG) log("- displayMainCallStatus: CNAP data from Connection: "
                                + "CNAP name=" + ci.cnapName
                                + ", Number/Name Presentation=" + ci.numberPresentation);
                        if (DBG) log("   ==> Got CallerInfo; updating display: ci = " + ci);
                        updateDisplayForPerson(ci, presentation, false, call);
                    } else if (o instanceof PhoneUtils.CallerInfoToken){
                        CallerInfo ci = ((PhoneUtils.CallerInfoToken) o).currentInfo;
                        if (DBG) log("- displayMainCallStatus: CNAP data from Connection: "
                                + "CNAP name=" + ci.cnapName
                                + ", Number/Name Presentation=" + ci.numberPresentation);
                        if (DBG) log("   ==> Got CallerInfoToken; updating display: ci = " + ci);
                        updateDisplayForPerson(ci, presentation, true, call);
                    } else {
                        Log.w(LOG_TAG, "displayMainCallStatus: runQuery was false, "
                              + "but we didn't have a cached CallerInfo object!  o = " + o);
                        // TODO: any easy way to recover here (given that
                        // the CallCard is probably displaying stale info
                        // right now?)  Maybe force the CallCard into the
                        // "Unknown" state?
                    }
                }
            }
        }

        // In some states we override the "photo" ImageView to be an
        // indication of the current state, rather than displaying the
        // regular photo as set above.
        updatePhotoForCallState(call);
    }

    /**
     * Version of displayMainCallStatus() that sets the main call area
     * into the "generic" state.
     * @see displayMainCallStatus
     */
    private void displayMainCallGeneric(Phone phone, Call call) {
        if (DBG) log("displayMainCallGeneric(phone " + phone
                     + ", call " + call + ")...");

        mMainCallCard.setVisibility(View.VISIBLE);

        // Background frame resources are different between portrait/landscape.
        // TODO: Don't do this manually.  Instead let the resource system do
        // it: just move the *_land assets over to the res/drawable-land
        // directory (but with the same filename as the corresponding
        // portrait asset.)
        boolean landscapeMode = InCallScreen.ConfigurationHelper.isLandscape();

        // Background images are also different if Bluetooth is active.
        final boolean bluetoothActive = mApplication.showBluetoothIndication();

        int callCardBackgroundResid = 0;
        if (bluetoothActive) {
            callCardBackgroundResid =
                    landscapeMode ? R.drawable.incall_frame_bluetooth_tall_land
                    : R.drawable.incall_frame_bluetooth_tall_port;
        } else {
            callCardBackgroundResid =
                    landscapeMode ? R.drawable.incall_frame_connected_tall_land
                    : R.drawable.incall_frame_connected_tall_port;
        }

        // Set the background frame color based on the state of the call.
        setMainCallCardBackgroundResource(callCardBackgroundResid);
        // (Text colors are set in updateCardTitleWidgets().)

        // Update timer field:
        // TODO(CDMA): Need to confirm that we can trust the time info
        // from the passed-in Call object, even though the call is "generic".
        if (DBG) log("displayMainCallStatus: start periodicUpdateTimer");
        mCallTime.setActiveCallMode(call);
        mCallTime.reset();
        mCallTime.periodicUpdateTimer();

        updateCardTitleWidgets(phone, call);
        updateDisplayForGenericCall();
    }

    /**
     * Implemented for CallerInfoAsyncQuery.OnQueryCompleteListener interface.
     * refreshes the CallCard data when it called.
     */
    public void onQueryComplete(int token, Object cookie, CallerInfo ci) {
        if (DBG) log("onQueryComplete: token " + token + ", cookie " + cookie + ", ci " + ci);

        if (cookie instanceof Call) {
            // grab the call object and update the display for an individual call,
            // as well as the successive call to update image via call state.
            // If the object is a textview instead, we update it as we need to.
            if (DBG) log("callerinfo query complete, updating ui from displayMainCallStatus()");
            Call call = (Call) cookie;
            Connection conn = call.getEarliestConnection();
            PhoneUtils.CallerInfoToken cit =
                   PhoneUtils.startGetCallerInfo(getContext(), conn, this, null);

            int presentation = Connection.PRESENTATION_ALLOWED;
            if (conn != null) presentation = conn.getNumberPresentation();
            if (DBG) log("- onQueryComplete: presentation=" + presentation
                    + ", contactExists=" + ci.contactExists);

            // Depending on whether there was a contact match or not, we want to pass in different
            // CallerInfo (for CNAP). Therefore if ci.contactExists then use the ci passed in.
            // Otherwise, regenerate the CIT from the Connection and use the CallerInfo from there.
            if (ci.contactExists) {
                updateDisplayForPerson(ci, Connection.PRESENTATION_ALLOWED, false, call);
            } else {
                updateDisplayForPerson(cit.currentInfo, presentation, false, call);
            }
            updatePhotoForCallState(call);

        } else if (cookie instanceof TextView){
            if (DBG) log("callerinfo query complete, updating ui from ongoing or onhold");
            ((TextView) cookie).setText(PhoneUtils.getCompactNameFromCallerInfo(ci, mContext));
        }
    }

    /**
     * Implemented for ContactsAsyncHelper.OnImageLoadCompleteListener interface.
     * make sure that the call state is reflected after the image is loaded.
     */
    public void onImageLoadComplete(int token, Object cookie, ImageView iView,
            boolean imagePresent){
        if (cookie != null) {
            updatePhotoForCallState((Call) cookie);
        }
    }

    /**
     * Updates the "upper" and "lower" titles based on the current state of this call.
     */
    private void updateCardTitleWidgets(Phone phone, Call call) {
        if (DBG) log("updateCardTitleWidgets(call " + call + ")...");
        Call.State state = call.getState();

        // TODO: Still need clearer spec on exactly how title *and* status get
        // set in all states.  (Then, given that info, refactor the code
        // here to be more clear about exactly which widgets on the card
        // need to be set.)

        // Normal "foreground" call card:
        String cardTitle = getTitleForCallCard(call);

        if (DBG) log("updateCardTitleWidgets: " + cardTitle);

        // We display *either* the "upper title" or the "lower title", but
        // never both.

        if (state == Call.State.ACTIVE) {
            final boolean bluetoothActive = mApplication.showBluetoothIndication();
            int ongoingCallIcon = bluetoothActive ? R.drawable.ic_incall_ongoing_bluetooth
                    : R.drawable.ic_incall_ongoing;
            int textColor = bluetoothActive ? mTextColorConnectedBluetooth : mTextColorConnected;

            if (mApplication.phone.getPhoneName().equals("CDMA")) {
               // Check if the "Dialing" 3Way call needs to be displayed
               // as the Foreground Call state still remains ACTIVE
               if (mApplication.cdmaPhoneCallState.IsThreeWayCallOrigStateDialing()) {
                    // Use the "upper title":
                    mUpperTitle.setText(cardTitle);
                    mLowerTitleViewGroup.setVisibility(View.INVISIBLE);
               } else {
                    // Use the "lower title" (in green).
                    mLowerTitleViewGroup.setVisibility(View.VISIBLE);
                    mLowerTitle.setText(cardTitle);
                    mLowerTitleIcon.setImageResource(ongoingCallIcon);
                    mLowerTitle.setTextColor(textColor);
                    mElapsedTime.setTextColor(textColor);
                    mUpperTitle.setText("");
               }
            } else { // GSM
                // Use the "lower title" (in green).
                mLowerTitleViewGroup.setVisibility(View.VISIBLE);
                mLowerTitleIcon.setImageResource(ongoingCallIcon);
                mLowerTitle.setText(cardTitle);
                mLowerTitle.setTextColor(textColor);
                mElapsedTime.setTextColor(textColor);
                setUpperTitle("");
            }
        } else if (state == Call.State.DISCONNECTED) {
            // Use the "lower title" (in red).
            // TODO: We may not *always* want to use the lower title for
            // the DISCONNECTED state.  "Error" states like BUSY or
            // CONGESTION (see getCallFailedString()) should probably go
            // in the upper title, for example.  In fact, the lower title
            // should probably be used *only* for the normal "Call ended"
            // case.
            mLowerTitleViewGroup.setVisibility(View.VISIBLE);
            mLowerTitleIcon.setImageResource(R.drawable.ic_incall_end);
            mLowerTitle.setText(cardTitle);
            mLowerTitle.setTextColor(mTextColorEnded);
            mElapsedTime.setTextColor(mTextColorEnded);
            setUpperTitle("");
        } else {
            // All other states (DIALING, INCOMING, etc.) use the "upper title":
            setUpperTitle(cardTitle, state);
            mLowerTitleViewGroup.setVisibility(View.INVISIBLE);
        }

        // Draw the onscreen "elapsed time" indication EXCEPT if we're in
        // the "Call ended" state.  (In that case, don't touch the
        // mElapsedTime widget, so we continue to see the elapsed time of
        // the call that just ended.)
        if (call.getState() == Call.State.DISCONNECTED) {
            // "Call ended" state -- don't touch the onscreen elapsed time.
        } else {
            long duration = CallTime.getCallDuration(call);  // msec
            updateElapsedTimeWidget(duration / 1000);
            // Also see onTickForCallTimeElapsed(), which updates this
            // widget once per second while the call is active.
        }
    }

    /**
     * Updates mElapsedTime based on the specified number of seconds.
     * A timeElapsed value of zero means to not show an elapsed time at all.
     */
    private void updateElapsedTimeWidget(long timeElapsed) {
        // if (DBG) log("updateElapsedTimeWidget: " + timeElapsed);
        if (timeElapsed == 0) {
            mElapsedTime.setText("");
        } else {
            mElapsedTime.setText(DateUtils.formatElapsedTime(timeElapsed));
        }
    }

    /**
     * Returns the "card title" displayed at the top of a foreground
     * ("active") CallCard to indicate the current state of this call, like
     * "Dialing" or "In call" or "On hold".  A null return value means that
     * there's no title string for this state.
     */
    private String getTitleForCallCard(Call call) {
        String retVal = null;
        Call.State state = call.getState();
        Context context = getContext();
        int resId;

        if (DBG) log("- getTitleForCallCard(Call " + call + ")...");

        switch (state) {
            case IDLE:
                break;

            case ACTIVE:
                // Title is "Call in progress".  (Note this appears in the
                // "lower title" area of the CallCard.)
                if (mApplication.phone.getPhoneName().equals("CDMA")) {
                    if (mApplication.cdmaPhoneCallState.IsThreeWayCallOrigStateDialing()) {
                        retVal = context.getString(R.string.card_title_dialing);
                    } else {
                        retVal = context.getString(R.string.card_title_in_progress);
                    }
                } else { //GSM
                    retVal = context.getString(R.string.card_title_in_progress);
                }
                break;

            case HOLDING:
                retVal = context.getString(R.string.card_title_on_hold);
                // TODO: if this is a conference call on hold,
                // maybe have a special title here too?
                break;

            case DIALING:
            case ALERTING:
                retVal = context.getString(R.string.card_title_dialing);
                break;

            case INCOMING:
            case WAITING:
                retVal = context.getString(R.string.card_title_incoming_call);
                break;

            case DISCONNECTED:
                retVal = getCallFailedString(call);
                break;
        }

        if (DBG) log("  ==> result: " + retVal);
        return retVal;
    }

    /**
     * Updates the "on hold" box in the "other call" info area
     * (ie. the stuff in the otherCallOnHoldInfo block)
     * based on the specified Call.
     * Or, clear out the "on hold" box if the specified call
     * is null or idle.
     */
    private void displayOnHoldCallStatus(Phone phone, Call call) {
        if (DBG) log("displayOnHoldCallStatus(call =" + call + ")...");
        if (call == null) {
            mOtherCallOnHoldInfoArea.setVisibility(View.GONE);
            return;
        }

        String name = null;
        Call.State state = call.getState();
        switch (state) {
            case HOLDING:
                // Ok, there actually is a background call on hold.
                // Display the "on hold" box.

                // First, see if we need to query.
                if (PhoneUtils.isConferenceCall(call)) {
                    if (DBG) log("==> conference call.");
                    name = getContext().getString(R.string.confCall);
                } else {
                    // perform query and update the name temporarily
                    // make sure we hand the textview we want updated to the
                    // callback function.
                    if (DBG) log("==> NOT a conf call; call startGetCallerInfo...");
                    PhoneUtils.CallerInfoToken info = PhoneUtils.startGetCallerInfo(
                            getContext(), call, this, mOtherCallOnHoldName);
                    name = PhoneUtils.getCompactNameFromCallerInfo(info.currentInfo, getContext());
                }

                mOtherCallOnHoldName.setText(name);

                // The call here is always "on hold", so use the orange "hold" frame
                // and orange text color:
                setOnHoldInfoAreaBackgroundResource(R.drawable.incall_frame_hold_short);
                mOtherCallOnHoldName.setTextColor(mTextColorOnHold);
                mOtherCallOnHoldStatus.setTextColor(mTextColorOnHold);

                mOtherCallOnHoldInfoArea.setVisibility(View.VISIBLE);

                break;

            case ACTIVE:
                // CDMA: This is because in CDMA when the user originates the second call,
                // although the Foreground call state is still ACTIVE in reality the network
                // put the first call on hold.
                if (mApplication.phone.getPhoneName().equals("CDMA")) {
                    List<Connection> connections = call.getConnections();
                    if (connections.size() > 2) {
                        // This means that current Mobile Originated call is the not the first 3-Way
                        // call the user is making, which in turn tells the PhoneApp that we no
                        // longer know which previous caller/party had dropped out before the user
                        // made this call.
                        name = getContext().getString(R.string.card_title_in_call);
                    } else {
                        // This means that the current Mobile Originated call IS the first 3-Way
                        // and hence we display the first callers/party's info here.
                        Connection conn = call.getEarliestConnection();
                        PhoneUtils.CallerInfoToken info = PhoneUtils.startGetCallerInfo(
                                getContext(), conn, this, mOtherCallOnHoldName);

                        name = PhoneUtils.getCompactNameFromCallerInfo(info.currentInfo,
                                getContext());
                    }

                    mOtherCallOnHoldName.setText(name);

                    // The call here is either in Callwaiting or 3way, use the orange "hold" frame
                    // and orange text color:
                    setOnHoldInfoAreaBackgroundResource(R.drawable.incall_frame_hold_short);
                    mOtherCallOnHoldName.setTextColor(mTextColorOnHold);
                    mOtherCallOnHoldStatus.setTextColor(mTextColorOnHold);
                    mOtherCallOnHoldInfoArea.setVisibility(View.VISIBLE);
                }
                break;

            default:
                // There's actually no call on hold.  (Presumably this call's
                // state is IDLE, since any other state is meaningless for the
                // background call.)
                mOtherCallOnHoldInfoArea.setVisibility(View.GONE);
                break;
        }
    }

    /**
     * Updates the "Ongoing call" box in the "other call" info area
     * (ie. the stuff in the otherCallOngoingInfo block)
     * based on the specified Call.
     * Or, clear out the "ongoing call" box if the specified call
     * is null or idle.
     */
    private void displayOngoingCallStatus(Phone phone, Call call) {
        if (DBG) log("displayOngoingCallStatus(call =" + call + ")...");
        if (call == null) {
            mOtherCallOngoingInfoArea.setVisibility(View.GONE);
            return;
        }

        Call.State state = call.getState();
        switch (state) {
            case ACTIVE:
            case DIALING:
            case ALERTING:
                // Ok, there actually is an ongoing call.
                // Display the "ongoing call" box.
                String name;

                // First, see if we need to query.
                if (call.isGeneric()) {
                    name = getContext().getString(R.string.card_title_in_call);
                } else if (PhoneUtils.isConferenceCall(call)) {
                    name = getContext().getString(R.string.confCall);
                } else {
                    // perform query and update the name temporarily
                    // make sure we hand the textview we want updated to the
                    // callback function.
                    PhoneUtils.CallerInfoToken info = PhoneUtils.startGetCallerInfo(
                            getContext(), call, this, mOtherCallOngoingName);
                    name = PhoneUtils.getCompactNameFromCallerInfo(info.currentInfo, getContext());
                }

                mOtherCallOngoingName.setText(name);

                // This is an "ongoing" call: we normally use the green
                // background frame and text color, but we use blue
                // instead if bluetooth is in use.
                boolean bluetoothActive = mApplication.showBluetoothIndication();

                int ongoingCallBackground =
                        bluetoothActive ? R.drawable.incall_frame_bluetooth_short
                        : R.drawable.incall_frame_connected_short;
                setOngoingInfoAreaBackgroundResource(ongoingCallBackground);

                int ongoingCallIcon = bluetoothActive ? R.drawable.ic_incall_ongoing_bluetooth
                        : R.drawable.ic_incall_ongoing;
                mOtherCallOngoingIcon.setImageResource(ongoingCallIcon);

                int textColor = bluetoothActive ? mTextColorConnectedBluetooth
                        : mTextColorConnected;
                mOtherCallOngoingName.setTextColor(textColor);
                mOtherCallOngoingStatus.setTextColor(textColor);

                mOtherCallOngoingInfoArea.setVisibility(View.VISIBLE);

                break;

            default:
                // There's actually no ongoing call.  (Presumably this call's
                // state is IDLE, since any other state is meaningless for the
                // foreground call.)
                mOtherCallOngoingInfoArea.setVisibility(View.GONE);
                break;
        }
    }


    private String getCallFailedString(Call call) {
        Connection c = call.getEarliestConnection();
        int resID;

        if (c == null) {
            if (DBG) log("getCallFailedString: connection is null, using default values.");
            // if this connection is null, just assume that the
            // default case occurs.
            resID = R.string.card_title_call_ended;
        } else {

            Connection.DisconnectCause cause = c.getDisconnectCause();

            // TODO: The card *title* should probably be "Call ended" in all
            // cases, but if the DisconnectCause was an error condition we should
            // probably also display the specific failure reason somewhere...

            switch (cause) {
                case BUSY:
                    resID = R.string.callFailed_userBusy;
                    break;

                case CONGESTION:
                    resID = R.string.callFailed_congestion;
                    break;

                case LOST_SIGNAL:
                    resID = R.string.callFailed_noSignal;
                    break;

                case LIMIT_EXCEEDED:
                    resID = R.string.callFailed_limitExceeded;
                    break;

                case POWER_OFF:
                    resID = R.string.callFailed_powerOff;
                    break;

                case ICC_ERROR:
                    resID = R.string.callFailed_simError;
                    break;

                case OUT_OF_SERVICE:
                    resID = R.string.callFailed_outOfService;
                    break;

                default:
                    resID = R.string.card_title_call_ended;
                    break;
            }
        }
        return getContext().getString(resID);
    }

    /**
     * Updates the name / photo / number / label fields on the CallCard
     * based on the specified CallerInfo.
     *
     * If the current call is a conference call, use
     * updateDisplayForConference() instead.
     *
     * If the phone is in the "generic call" state, use
     * updateDisplayForGenericCall() instead.
     */
    private void updateDisplayForPerson(CallerInfo info,
                                        int presentation,
                                        boolean isTemporary,
                                        Call call) {
        if (DBG) log("updateDisplayForPerson(" + info + ")...");

        // inform the state machine that we are displaying a photo.
        mPhotoTracker.setPhotoRequest(info);
        mPhotoTracker.setPhotoState(ContactsAsyncHelper.ImageTracker.DISPLAY_IMAGE);

        String name;
        String displayNumber = null;
        String label = null;
        Uri personUri = null;

boolean updateName = false;

        if (info != null) {
            // It appears that there is a small change in behaviour with the
            // PhoneUtils' startGetCallerInfo whereby if we query with an
            // empty number, we will get a valid CallerInfo object, but with
            // fields that are all null, and the isTemporary boolean input
            // parameter as true.

            // In the past, we would see a NULL callerinfo object, but this
            // ends up causing null pointer exceptions elsewhere down the
            // line in other cases, so we need to make this fix instead. It
            // appears that this was the ONLY call to PhoneUtils
            // .getCallerInfo() that relied on a NULL CallerInfo to indicate
            // an unknown contact.

            if (TextUtils.isEmpty(info.name)) {
                if (TextUtils.isEmpty(info.phoneNumber)) {
                    name =  getPresentationString(presentation);
                } else if (presentation != Connection.PRESENTATION_ALLOWED) {
                    // This case should never happen since the network should never send a phone #
                    // AND a restricted presentation. However we leave it here in case of weird
                    // network behavior
                    name = getPresentationString(presentation);
                } else if (!TextUtils.isEmpty(info.cnapName)) {
                    name = info.cnapName;
                    info.name = info.cnapName;
                    displayNumber = info.phoneNumber;
                } else {
                    name = info.phoneNumber;
                }
            } else {
                if (presentation != Connection.PRESENTATION_ALLOWED) {
                    // This case should never happen since the network should never send a name
                    // AND a restricted presentation. However we leave it here in case of weird
                    // network behavior
                    name = getPresentationString(presentation);
                } else {
                    name = info.name;
                    displayNumber = info.phoneNumber;
                    label = info.phoneLabel;
// add by cytown for show organization
updateName = true;
                }
            }
            personUri = ContentUris.withAppendedId(People.CONTENT_URI, info.person_id);
        } else {
            name =  getPresentationString(presentation);
        }

        if (call.isGeneric()) {
            mName.setText(R.string.card_title_in_call);
        } else {
String oldname = mName.getText().toString();
if (oldname == null || !(oldname.equals(name) || oldname.startsWith(name + "-"))) {
            mName.setText(name);
    if (updateName && mSettings.mShowOrgan) {
        updateOrganization(info.person_id);
    }
}
        }
        mName.setVisibility(View.VISIBLE);

        // Update mPhoto
        // if the temporary flag is set, we know we'll be getting another call after
        // the CallerInfo has been correctly updated.  So, we can skip the image
        // loading until then.

        // If the photoResource is filled in for the CallerInfo, (like with the
        // Emergency Number case), then we can just set the photo image without
        // requesting for an image load. Please refer to CallerInfoAsyncQuery.java
        // for cases where CallerInfo.photoResource may be set.  We can also avoid
        // the image load step if the image data is cached.
        if (isTemporary && (info == null || !info.isCachedPhotoCurrent)) {
            mPhoto.setVisibility(View.INVISIBLE);
        } else if (info != null && info.photoResource != 0){
            showImage(mPhoto, info.photoResource);
        } else if (!showCachedImage(mPhoto, info)) {
            // Load the image with a callback to update the image state.
            // Use a placeholder image value of -1 to indicate no image.
            ContactsAsyncHelper.updateImageViewWithContactPhotoAsync(info, 0, this, call,
                    getContext(), mPhoto, personUri, -1);
        }
        if (displayNumber != null && !call.isGeneric()) {
            mPhoneNumber.setText(displayNumber);
            mPhoneNumber.setVisibility(View.VISIBLE);
        } else {
            mPhoneNumber.setVisibility(View.GONE);
        }

        if (label != null) {
            mLabel.setText(label);
            mLabel.setVisibility(View.VISIBLE);
        } else {
            mLabel.setVisibility(View.GONE);
        }
    }

private void updateOrganization(final long person_id) {
//    Thread t = new Thread(new Runnable() {

//        public void run() {
            android.database.Cursor c = CallCard.this.getContext().getContentResolver().query(Organizations.CONTENT_URI,
                    new String[] { Organizations.COMPANY },
                    Organizations.PERSON_ID + " = ? and " + Organizations.ISPRIMARY + " > ?", new String[] { person_id + "", "0" },
                    null);
            if (c != null) {
                if (c.moveToNext()) {
                    try {
//System.out.println("show organ");
                        mName.setText(mName.getText() + "-" + c.getString(0));
//                        mName.invalidate();
                    } catch (Exception e) {}
                }
                c.close();
            }
//        }
//    });
//    t.start();
}

    private String getPresentationString(int presentation) {
        String name = getContext().getString(R.string.unknown);
        if (presentation == Connection.PRESENTATION_RESTRICTED) {
            name = getContext().getString(R.string.private_num);
        } else if (presentation == Connection.PRESENTATION_PAYPHONE) {
            name = getContext().getString(R.string.payphone);
        }
        return name;
    }

    /**
     * Updates the name / photo / number / label fields
     * for the special "conference call" state.
     *
     * If the current call has only a single connection, use
     * updateDisplayForPerson() instead.
     */
    private void updateDisplayForConference() {
        if (DBG) log("updateDisplayForConference()...");

        if (mApplication.phone.getPhoneName().equals("CDMA")) {
            // This state corresponds to both 3-Way merged call and
            // Call Waiting accepted call.
            // Display only the "dialing" icon and no caller information cause in CDMA
            // as in this state the user does not really know which caller party he is talking to.
            showImage(mPhoto, R.drawable.picture_dialing);
            mName.setText(R.string.card_title_in_call);
        } else {
            // Display the "conference call" image in the photo slot,
            // with no other information.
            showImage(mPhoto, R.drawable.picture_conference);
            mName.setText(R.string.card_title_conf_call);
        }

        mName.setVisibility(View.VISIBLE);

        // TODO: For a conference call, the "phone number" slot is specced
        // to contain a summary of who's on the call, like "Bill Foldes
        // and Hazel Nutt" or "Bill Foldes and 2 others".
        // But for now, just hide it:
        mPhoneNumber.setVisibility(View.GONE);

        mLabel.setVisibility(View.GONE);

        // TODO: consider also showing names / numbers / photos of some of the
        // people on the conference here, so you can see that info without
        // having to click "Manage conference".  We probably have enough
        // space to show info for 2 people, at least.
        //
        // To do this, our caller would pass us the activeConnections
        // list, and we'd call PhoneUtils.getCallerInfo() separately for
        // each connection.
    }

    /**
     * Updates the name / photo / number / label fields
     * for the special "generic call" state.
     * @see updateDisplayForPerson
     * @see updateDisplayForConference
     */
    private void updateDisplayForGenericCall() {
        if (DBG) log("updateDisplayForGenericCall()...");

        // Display a generic "in-call" image in the photo slot, with no
        // other information.

        showImage(mPhoto, R.drawable.picture_dialing);

        mName.setVisibility(View.GONE);
        mPhoneNumber.setVisibility(View.GONE);
        mLabel.setVisibility(View.GONE);
    }

    /**
     * Updates the CallCard "photo" IFF the specified Call is in a state
     * that needs a special photo (like "busy" or "dialing".)
     *
     * If the current call does not require a special image in the "photo"
     * slot onscreen, don't do anything, since presumably the photo image
     * has already been set (to the photo of the person we're talking, or
     * the generic "picture_unknown" image, or the "conference call"
     * image.)
     */
    private void updatePhotoForCallState(Call call) {
        if (DBG) log("updatePhotoForCallState(" + call + ")...");
        int photoImageResource = 0;

        // Check for the (relatively few) telephony states that need a
        // special image in the "photo" slot.
        Call.State state = call.getState();
        switch (state) {
            case DISCONNECTED:
                // Display the special "busy" photo for BUSY or CONGESTION.
                // Otherwise (presumably the normal "call ended" state)
                // leave the photo alone.
                Connection c = call.getEarliestConnection();
                // if the connection is null, we assume the default case,
                // otherwise update the image resource normally.
                if (c != null) {
                    Connection.DisconnectCause cause = c.getDisconnectCause();
                    if ((cause == Connection.DisconnectCause.BUSY)
                        || (cause == Connection.DisconnectCause.CONGESTION)) {
                        photoImageResource = R.drawable.picture_busy;
                    }
                } else if (DBG) {
                    log("updatePhotoForCallState: connection is null, ignoring.");
                }

                // TODO: add special images for any other DisconnectCauses?
                break;

            case DIALING:
            case ALERTING:
                photoImageResource = R.drawable.picture_dialing;
                break;

            default:
                // Leave the photo alone in all other states.
                // If this call is an individual call, and the image is currently
                // displaying a state, (rather than a photo), we'll need to update
                // the image.
                // This is for the case where we've been displaying the state and
                // now we need to restore the photo.  This can happen because we
                // only query the CallerInfo once, and limit the number of times
                // the image is loaded. (So a state image may overwrite the photo
                // and we would otherwise have no way of displaying the photo when
                // the state goes away.)

                // if the photoResource field is filled-in in the Connection's
                // caller info, then we can just use that instead of requesting
                // for a photo load.

                // look for the photoResource if it is available.
                CallerInfo ci = null;
                {
                    Connection conn = null;
                    if (mApplication.phone.getPhoneName().equals("CDMA")) {
                        conn = call.getLatestConnection();
                    } else { // GSM.
                        conn = call.getEarliestConnection();
                    }

                    if (conn != null) {
                        Object o = conn.getUserData();
                        if (o instanceof CallerInfo) {
                            ci = (CallerInfo) o;
                        } else if (o instanceof PhoneUtils.CallerInfoToken) {
                            ci = ((PhoneUtils.CallerInfoToken) o).currentInfo;
                        }
                    }
                }

                if (ci != null) {
                    photoImageResource = ci.photoResource;
                }

                // If no photoResource found, check to see if this is a conference call. If
                // it is not a conference call:
                //   1. Try to show the cached image
                //   2. If the image is not cached, check to see if a load request has been
                //      made already.
                //   3. If the load request has not been made [DISPLAY_DEFAULT], start the
                //      request and note that it has started by updating photo state with
                //      [DISPLAY_IMAGE].
                // Load requests started in (3) use a placeholder image of -1 to hide the
                // image by default.  Please refer to CallerInfoAsyncQuery.java for cases
                // where CallerInfo.photoResource may be set.
                if (photoImageResource == 0) {
                    if (!PhoneUtils.isConferenceCall(call)) {
                        if (!showCachedImage(mPhoto, ci) && (mPhotoTracker.getPhotoState() ==
                                ContactsAsyncHelper.ImageTracker.DISPLAY_DEFAULT)) {
                            ContactsAsyncHelper.updateImageViewWithContactPhotoAsync(ci,
                                    getContext(), mPhoto, mPhotoTracker.getPhotoUri(), -1);
                            mPhotoTracker.setPhotoState(
                                    ContactsAsyncHelper.ImageTracker.DISPLAY_IMAGE);
                        }
                    }
                } else {
                    showImage(mPhoto, photoImageResource);
                    mPhotoTracker.setPhotoState(ContactsAsyncHelper.ImageTracker.DISPLAY_IMAGE);
                    return;
                }
                break;
        }

        if (photoImageResource != 0) {
            if (DBG) log("- overrriding photo image: " + photoImageResource);
            showImage(mPhoto, photoImageResource);
            // Track the image state.
            mPhotoTracker.setPhotoState(ContactsAsyncHelper.ImageTracker.DISPLAY_DEFAULT);
        }
    }

    /**
     * Try to display the cached image from the callerinfo object.
     *
     *  @return true if we were able to find the image in the cache, false otherwise.
     */
    private static final boolean showCachedImage (ImageView view, CallerInfo ci) {
        if ((ci != null) && ci.isCachedPhotoCurrent) {
            if (ci.cachedPhoto != null) {
                showImage(view, ci.cachedPhoto);
            } else {
                showImage(view, R.drawable.picture_unknown);
            }
            return true;
        }
        return false;
    }

    /** Helper function to display the resource in the imageview AND ensure its visibility.*/
    private static final void showImage(ImageView view, int resource) {
        view.setImageResource(resource);
        view.setVisibility(View.VISIBLE);
    }

    /** Helper function to display the drawable in the imageview AND ensure its visibility.*/
    private static final void showImage(ImageView view, Drawable drawable) {
        view.setImageDrawable(drawable);
        view.setVisibility(View.VISIBLE);
    }

    /**
     * Intercepts (and discards) any touch events to the CallCard.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // if (DBG) log("CALLCARD: dispatchTouchEvent(): ev = " + ev);

        // We *never* let touch events get thru to the UI inside the
        // CallCard, since there's nothing touchable there.
        return true;
    }

    /**
     * Sets the background drawable of the main call card.
     */
    private void setMainCallCardBackgroundResource(int resid) {
        mMainCallCard.setBackgroundResource(resid);
    }

    /**
     * Sets the background drawable of the "ongoing call" info area.
     */
    private void setOngoingInfoAreaBackgroundResource(int resid) {
        mOtherCallOngoingInfoArea.setBackgroundResource(resid);
    }

    /**
     * Sets the background drawable of the "call on hold" info area.
     */
    private void setOnHoldInfoAreaBackgroundResource(int resid) {
        mOtherCallOnHoldInfoArea.setBackgroundResource(resid);
    }

    /**
     * Returns the "Menu button hint" TextView (which is manipulated
     * directly by the InCallScreen.)
     * @see InCallScreen.updateMenuButtonHint()
     */
    /* package */ TextView getMenuButtonHint() {
        return mMenuButtonHint;
    }

    /**
     * Updates anything about our View hierarchy or internal state
     * that needs to be different in landscape mode.
     *
     * @see InCallScreen.applyConfigurationToLayout()
     */
    /* package */ void updateForLandscapeMode() {
        if (DBG) log("updateForLandscapeMode()...");

        // The main CallCard's minimum height is smaller in landscape mode
        // than in portrait mode.
        mMainCallCard.setMinimumHeight(MAIN_CALLCARD_MIN_HEIGHT_LANDSCAPE);

        // Add some left and right margin to the top-level elements, since
        // there's no need to use the full width of the screen (which is
        // much wider in landscape mode.)
        setSideMargins(mMainCallCard, CALLCARD_SIDE_MARGIN_LANDSCAPE);
        setSideMargins(mOtherCallOngoingInfoArea, CALLCARD_SIDE_MARGIN_LANDSCAPE);
        setSideMargins(mOtherCallOnHoldInfoArea, CALLCARD_SIDE_MARGIN_LANDSCAPE);

        // A couple of TextViews are slightly smaller in landscape mode.
        mUpperTitle.setTextSize(TITLE_TEXT_SIZE_LANDSCAPE);
    }

    /**
     * Sets the left and right margins of the specified ViewGroup (whose
     * LayoutParams object which must inherit from
     * ViewGroup.MarginLayoutParams.)
     *
     * TODO: Is there already a convenience method like this somewhere?
     */
    private void setSideMargins(ViewGroup vg, int margin) {
        ViewGroup.MarginLayoutParams lp =
                (ViewGroup.MarginLayoutParams) vg.getLayoutParams();
        // Equivalent to setting android:layout_marginLeft/Right in XML
        lp.leftMargin = margin;
        lp.rightMargin = margin;
        vg.setLayoutParams(lp);
    }

    /**
     * Sets the CallCard "upper title" to a plain string, with no icon.
     */
    private void setUpperTitle(String title) {
        mUpperTitle.setText(title);
        mUpperTitle.setCompoundDrawables(null, null, null, null);
    }

    /**
     * Sets the CallCard "upper title".  Also, depending on the passed-in
     * Call state, possibly display an icon along with the title.
     */
    private void setUpperTitle(String title, Call.State state) {
        mUpperTitle.setText(title);

        int bluetoothIconId = 0;
        if (((state == Call.State.INCOMING) || (state == Call.State.WAITING))
                && mApplication.showBluetoothIndication()) {
            // Display the special bluetooth icon also, if this is an incoming
            // call and the audio will be routed to bluetooth.
            bluetoothIconId = R.drawable.ic_incoming_call_bluetooth;
        }

        mUpperTitle.setCompoundDrawablesWithIntrinsicBounds(bluetoothIconId, 0, 0, 0);
        if (bluetoothIconId != 0) mUpperTitle.setCompoundDrawablePadding(5);
    }


    // Debugging / testing code

    private void log(String msg) {
        Log.d(LOG_TAG, msg);
    }

    private static void logErr(String msg) {
        Log.e(LOG_TAG, msg);
    }
}
