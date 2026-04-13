# VigileX — Quick Setup (5 minutes)

## Step 1 — Install Android Studio (if not installed)

1. Download from: https://developer.android.com/studio
2. Run installer → accept defaults → let it install Android SDK + emulator
3. On first launch: finish the setup wizard → install recommended SDK

---

## Step 2 — Firebase Setup (get google-services.json)

1. Open: https://console.firebase.google.com
2. Click **"Add project"** → name it **VigileX** → Continue
3. Disable Google Analytics → **Create project** → wait ~30 seconds
4. On the project home, click the **Android icon** (</> button)
5. Fill in:
   - **Android package name:** `com.vigilex`
   - **App nickname:** VigileX
   - Skip SHA-1 for now
6. Click **Register app**
7. Click **Download google-services.json**
8. **Replace** the file at: `C:\Users\v-vashisthap\vigilex\app\google-services.json`
9. Click **Next → Next → Continue to Console** (skip the SDK steps)

### Enable Authentication
- Left sidebar → **Authentication** → Get started → **Email/Password** → Enable → **Save**

### Enable Firestore
- Left sidebar → **Firestore Database** → Create database → **Start in test mode** → Choose your region → **Enable**

---

## Step 3 — Open Project in Android Studio

1. Open Android Studio
2. **File → Open** → navigate to `C:\Users\v-vashisthap\vigilex` → click **OK**
3. Wait for Gradle sync (first time downloads ~500MB, takes a few minutes)
4. If it asks to upgrade Gradle → click **Don't remind me again**

---

## Step 4 — Create Emulator

1. In Android Studio → **Device Manager** (right panel or Tools menu)
2. Click **+** → **Create Virtual Device**
3. Choose **Pixel 8** → Next
4. Select **API 34 (UpsideDownCake)** → Download if needed → Next → Finish
5. Click the **▶ Play** button next to the device to start it

---

## Step 5 — Run the App

1. Make sure your Maps API key is in `local.properties`:
   ```
   MAPS_API_KEY=your_actual_key_here
   ```
2. Select your emulator from the device dropdown in Android Studio toolbar
3. Press **Shift+F10** (or click the green **▶ Run** button)
4. The app builds and launches (~2 min first time)

---

## First Login

- Email: `pravs.x@gmail.com`
- Password: (choose any password — this becomes your Super Admin password on first login)

The app seeds the Super Admin Firestore document automatically on first login.
