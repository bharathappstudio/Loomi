package com.echo.loomi

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MessageListenerService : Service() {

    private val database = FirebaseDatabase.getInstance("https://echo-loomi-app-default-rtdb.firebaseio.com/").reference
    private val listeners = mutableMapOf<String, ValueEventListener>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationHelper.createNotificationChannel(this)
        startListening()
        return START_STICKY
    }

    private fun startListening() {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        database.child("users").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (userSnapshot in snapshot.children) {
                    val otherUid = userSnapshot.child("uid").getValue(String::class.java) ?: continue
                    val otherName = userSnapshot.child("name").getValue(String::class.java) ?: "Unknown"
                    val otherImage = userSnapshot.child("imageName").getValue(String::class.java) ?: ""

                    if (otherUid == myUid) continue
                    
                    val chatId = if (myUid < otherUid) "${myUid}_$otherUid" else "${otherUid}_$myUid"
                    
                    if (!listeners.containsKey(chatId)) {
                        val listener = object : ValueEventListener {
                            private var firstLoad = true
                            override fun onDataChange(chatSnapshot: DataSnapshot) {
                                if (chatSnapshot.exists()) {
                                    val lastMsgObj = chatSnapshot.children.lastOrNull()?.getValue(ChatMessage::class.java)
                                    if (lastMsgObj != null && lastMsgObj.senderId != myUid) {
                                        val now = System.currentTimeMillis()
                                        // Only notify if message is less than 15 seconds old and it's not the first load
                                        if (!firstLoad && (now - lastMsgObj.timestamp) < 15000) {
                                            NotificationHelper.showMessageNotification(
                                                this@MessageListenerService,
                                                otherUid,
                                                otherName,
                                                otherImage,
                                                lastMsgObj.message,
                                                chatId
                                            )
                                        }
                                    }
                                }
                                firstLoad = false
                            }
                            override fun onCancelled(error: DatabaseError) {}
                        }
                        database.child("chats").child(chatId).limitToLast(1).addValueEventListener(listener)
                        listeners[chatId] = listener
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        listeners.forEach { (chatId, listener) ->
            database.child("chats").child(chatId).removeEventListener(listener)
        }
        super.onDestroy()
    }
}
