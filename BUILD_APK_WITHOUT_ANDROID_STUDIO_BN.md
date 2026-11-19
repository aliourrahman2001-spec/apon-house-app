# Apon House APK তৈরি — Android Studio ছাড়াই (GitHub Actions)

এই project-এ `.github/workflows/build-apk.yml` দেওয়া আছে। GitHub-এর cloud runner-এ Java 17 ও Gradle 8.9 দিয়ে Android APK build করার চেষ্টা করবে।

## আপনার তৈরি করা repository ব্যবহার করুন

1. GitHub-এ আপনার তৈরি করা `apon-house-app` repository খুলুন।
2. **Add file → Upload files** নির্বাচন করুন (নতুন খালি repository-তে “uploading an existing file” লিংকও থাকতে পারে)।
3. এই ZIP-টি কম্পিউটারে **Extract All** করুন। Extract করা ফোল্ডার খুলুন।
4. ওই ফোল্ডারের ভেতরের **সব ফাইল ও ফোল্ডার** repository-র upload area-তে টেনে দিন। `app`, `gradle`, `.github` ফোল্ডার এবং `build.gradle.kts`, `settings.gradle.kts`-সহ সব ফাইল দিতে হবে। ZIP ফাইলটিকে একা upload করবেন না।
5. পেজের নিচে **Commit changes** চাপুন। Branch `main`-এ commit করুন।
6. Repository-র **Actions** tab খুলুন। `Build Apon House APK` workflow চলতে শুরু করার কথা। না চললে workflow খুলে **Run workflow** চাপুন।
7. Build সফল হলে workflow run খুলে নিচের **Artifacts** অংশ থেকে `AponHouse-debug-apk` ডাউনলোড করুন।
8. ডাউনলোড করা artifact ZIP extract করলে `app-debug.apk` পাবেন। APK Android ফোনে পাঠিয়ে install করুন।

## জরুরি কথা

- এই ZIP হলো Android source project; **APK নয়**। GitHub Actions build সফল হলেই APK পাওয়া যাবে।
- এই source এখানে Android SDK দিয়ে build বা ফোনে পরীক্ষা করা হয়নি। GitHub build-এ error এলে Actions-এর error log/স্ক্রিনশট পাঠাবেন; সেই error দেখে ঠিক করতে হবে।
- এটি debug APK; Play Store-এ প্রকাশের জন্য signed release APK নয়।
- Firebase config `app/google-services.json`-এ রাখা আছে। এটি Android app-এর client configuration। Firebase password, owner login password, বা Firebase Admin service-account private key কখনো GitHub-এ upload করবেন না।
- Firebase Console-এ Owner-এর `members/{UID}` document-এ `role` lowercase `owner` এবং `active` boolean `true` থাকতে হবে।
- App-এর Firestore data access-এর জন্য এই project-এর `firestore.rules` Firebase Console → Firestore Database → Rules-এ publish করতে হবে। আগে থেকে অন্য rules publish করা থাকলে এই file-এর rules-এর সঙ্গে মিলিয়ে update করতে হবে। Rules publish করার আগে পড়ে নেবেন।
- এই সংস্করণে daily meals, guest meals, payment/expense entry, basic dashboard, settings এবং date-based duty rotation-এর starter modules আছে। Monthly carry-forward, automatic fine posting, full member-wise due calculation, duty completion button/history, monthly PDF/Excel export ইত্যাদি এখনো সম্পূর্ণ নয়। তাই একে production-complete বলা যাবে না।
