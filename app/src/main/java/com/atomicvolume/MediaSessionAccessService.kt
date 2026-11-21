package com.atomicvolume

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MediaSessionAccessService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        sendRefresh()
    }
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        sendRefresh()
    }
    override fun onNotificationPosted(sbn: StatusBarNotification?) { sendRefresh() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { sendRefresh() }
    private fun sendRefresh() {
        sendBroadcast(Intent(ACTION_REFRESH).setPackage(packageName))
    }
    companion object {
        const val ACTION_REFRESH = "com.atomicvolume.ACTION_REFRESH_MEDIA_SESSIONS"
    }
}
