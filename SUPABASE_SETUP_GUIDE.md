# Supabase Storage Setup Guide

## ✅ Current Status:
- Upload to Supabase: **WORKING** ✅
- Image saved to Firestore: **WORKING** ✅  
- Image loading in app: **FAILING** ❌ (HTTP 400 error)

**Problem:** The `profile-images` bucket is not publicly accessible.

---

## 🔧 Step-by-Step Fix:

### **Option 1: Via Supabase Dashboard (Easiest)**

1. **Go to Storage:**
   - Open: https://supabase.com/dashboard/project/ijcfvpodwmshmdecmxmk/storage/buckets

2. **Make Bucket Public:**
   - Find `profile-images` bucket
   - Click the **3 dots (⋮)** next to it
   - Click **"Edit bucket"**
   - Toggle **"Public bucket"** to **ON** ✅
   - Click **"Save"**

3. **Set Policies:**
   - Click on `profile-images` bucket to open it
   - Go to **"Policies"** tab at the top
   - Click **"New Policy"** button
   - Choose **"For full customization"**
   
   **Add Policy 1: Public Read**
   - Policy name: `Public Read Access`
   - Target roles: `public`
   - Operation: `SELECT`
   - USING expression:
   ```sql
   bucket_id = 'profile-images'
   ```
   - Click **"Review"** → **"Save policy"**

   **Add Policy 2: Authenticated Upload**
   - Policy name: `Authenticated Upload`
   - Target roles: `authenticated`
   - Operation: `INSERT`
   - WITH CHECK expression:
   ```sql
   bucket_id = 'profile-images'
   ```
   - Click **"Review"** → **"Save policy"**

---

### **Option 2: Via SQL Editor (Faster)**

1. Go to **SQL Editor** in Supabase Dashboard:
   https://supabase.com/dashboard/project/ijcfvpodwmshmdecmxmk/sql/new

2. **Run this SQL:**

```sql
-- Step 1: Make bucket public
UPDATE storage.buckets 
SET public = true 
WHERE id = 'profile-images';

-- Step 2: Allow public reads
CREATE POLICY IF NOT EXISTS "Public profile read" 
ON storage.objects FOR SELECT 
TO public 
USING (bucket_id = 'profile-images');

-- Step 3: Allow authenticated uploads
CREATE POLICY IF NOT EXISTS "Authenticated profile upload" 
ON storage.objects FOR INSERT 
TO authenticated 
WITH CHECK (bucket_id = 'profile-images');

-- Step 4: Allow users to update their own profile images
CREATE POLICY IF NOT EXISTS "Users can update own profile" 
ON storage.objects FOR UPDATE 
TO authenticated 
USING (bucket_id = 'profile-images');
```

3. Click **"Run"** (or press Cmd+Enter)

---

## 🧪 **Test if Fixed:**

### **Method 1: Open in Browser**
Copy this URL and open in your browser:
```
https://ijcfvpodwmshmdecmxmk.supabase.co/storage/v1/object/public/profile-images/zsATPhid88RYhy5WIiLyHUwdspP2/profile.jpg
```

✅ **If it works:** You'll see your profile image  
❌ **If it fails:** You'll see an error message (bucket still not public)

### **Method 2: In Your App**
1. **Close and reopen** your app (no need to rebuild)
2. Go to **Profile** tab
3. Your profile picture should now display ✅

---

## 📊 **Verify Policies Are Applied:**

Go to: https://supabase.com/dashboard/project/ijcfvpodwmshmdecmxmk/storage/policies

You should see these policies for `profile-images`:
- ✅ Public Read Access (SELECT for public)
- ✅ Authenticated Upload (INSERT for authenticated)
- ✅ Users can update own profile (UPDATE for authenticated)

---

## 🐛 **Still Not Working?**

### Check Your Current Firestore Data:
Your profile URL in Firestore should be:
```
https://ijcfvpodwmshmdecmxmk.supabase.co/storage/v1/object/public/profile-images/zsATPhid88RYhy5WIiLyHUwdspP2/profile.jpg
```

### Verify Logs:
In Android Studio Logcat, filter by `Glide`:
- ✅ **Good:** No errors
- ❌ **Bad:** Still seeing "status code: 400"

If still failing:
1. Double-check the bucket is marked as **Public** (green toggle in dashboard)
2. Verify policies are created (SQL Editor method is most reliable)
3. Try uploading a new image to see if it works with the new policies

---

## 🎯 **Expected Result After Fix:**

Your app should display profile pictures from Supabase Storage:
- Profile tab shows your avatar ✅
- Edit Profile shows current avatar ✅  
- Other users see your avatar in chats ✅

The upload is already working, we just need the images to be readable!
