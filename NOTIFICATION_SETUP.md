# 🔔 Notification System - Setup Guide

## ✅ What We Did

We've implemented a **professional notification system** that works **WITHOUT a backend server**! 

### How It Works:
1. **User A** sends a message to **User B**
2. Message data is written to Firestore: `users/UserB/notifications/{autoId}`
3. **User B's** app has a background service (`NotificationListenerService`) listening to that Firestore collection
4. When new document is added, **User B** gets a notification at the top of their phone! 🎉

## 📝 Steps to Enable Notifications

### Step 1: Deploy Firestore Security Rules

Open Firebase Console and update your Firestore rules:

```bash
# Go to Firebase Console → Firestore Database → Rules
# Then paste the content from firestore.rules file
```

Or use Firebase CLI:

```bash
firebase deploy --only firestore:rules
```

### Step 2: Build and Test

```bash
./gradlew assembleDebug
```

Install on **two devices** with **two different accounts**.

### Step 3: Test the Notification

1. **Device A**: Login as User A (e.g., Ah Poy Kh)
2. **Device B**: Login as User B (e.g., your test user)
3. **Device A**: Send a message to User B
4. **Device B**: Should see notification! 🔔

## 🔍 Troubleshooting

### "No notification appears"

**Check these logs on Device B** (receiver):

```
NotificationListener: 👂 Starting to listen for notifications...
NotificationListener: 🔔 New notification received!
NotificationListener: 📢 From: Ah Poy Kh
NotificationListener: 📝 Message: Hello!
NotificationListener: ✅ Notification shown!
```

**If you see:** `❌ Listen failed: Missing or insufficient permissions`
→ Deploy Firestore rules (Step 1)

**If you see:** `⚠️ No user logged in, stopping service`
→ Make sure user is logged in before HomeActivity starts

**If notifications don't show:**
→ Check Device B has notification permissions enabled
→ Go to Android Settings → Apps → EZ Talk → Notifications → Enable

### Check if service is running:

```
# Device B logs:
HomeActivity: ✅ NotificationListenerService started
NotificationListener: 👂 Starting to listen for notifications...
```

## 📊 How to Read Logs

### When User A sends message:

**Device A (Sender):**
```
MessageNotificationManager: 📤 Sending notification
MessageNotificationManager:    To: e8vfGQ7WpyLBXmBwYkbnKTu6PTH3
MessageNotificationManager:    From: Ah Poy Kh
MessageNotificationManager:    Type: TEXT
MessageNotificationManager: ✅ Notification data written to Firestore!
```

**Device B (Receiver):**
```
NotificationListener: 🔔 New notification received!
NotificationListener: 📢 From: Ah Poy Kh
NotificationListener: 📝 Message: Hello!
NotificationListener: 📊 Type: TEXT
NotificationListener: ✅ Notification shown!
NotificationListener: 🗑️ Notification document deleted
```

## 🎨 Notification Features

✅ Professional **MessagingStyle** (like WhatsApp)
✅ Shows **sender avatar** (circular)
✅ Message type icons:
   - 📷 Photo
   - 🎤 Voice message  
   - 🎥 Video
   - 📄 File
   - 📍 Location
✅ **Sound** and **vibration**
✅ Opens chat when tapped
✅ Auto-deletes after showing (no clutter)

## 🚀 Future: Backend Server (Optional)

If you want to use a backend server instead of Firestore triggers:

1. Deploy Node.js server to Railway/Heroku
2. In `MessageNotificationManager.java`, change:
   ```java
   private static final boolean USE_FIRESTORE_TRIGGER = false;
   ```
3. Update `BACKEND_URL` to your server URL

**Pros of Backend:**
- More control over notifications
- Can send to multiple devices
- Better for large scale

**Pros of Firestore (Current):**
- No backend needed! 🎉
- Simpler setup
- Perfect for small/medium apps
- Works offline (notifications queued)

## 🔐 Firestore Rules Explained

```javascript
// Allow anyone to write notifications
match /notifications/{notificationId} {
  allow read: if request.auth.uid == userId;  // Only receiver reads
  allow write: if request.auth != null;        // Any user can write (send)
}
```

This allows User A to send notification to User B!

## ✅ Success Checklist

- [ ] Firestore rules deployed
- [ ] App built and installed on 2 devices
- [ ] Notification permission enabled on both devices
- [ ] Logged in with different accounts
- [ ] Message sent from Device A
- [ ] Notification received on Device B
- [ ] Sound and vibration working
- [ ] Tapping notification opens chat

## 🎉 Done!

Your notification system is now working like WhatsApp/Telegram!

No backend server needed! 🚀
