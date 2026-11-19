# Apon House — Firebase সংযুক্ত Android Source

এই প্রজেক্টে আপনার আপলোড করা `google-services.json` ফাইলটি `app/google-services.json`-এ বসানো হয়েছে। ফাইল থেকে যাচাই করা Android package name: `com.aponhouse.mess`; Firebase project ID: `apon-house`।

## এই Source-এ যা আছে

- Android Kotlin + Jetpack Compose app shell (Green & White theme)
- Firebase Email/Password Authentication
- Firestore real-time listeners
- Member sign-up: নতুন account প্রথমে `pending` থাকবে
- Role model: `owner`, `monthly_manager`, `monitor` = Admin; `member` = Member
- Dashboard: recorded payments, expenses, entries এবং balance
- Daily meal entry: সকাল ৳10, বিকাল ৳25, রাত ৳10 (Settings থেকে configurable)
- Guest meal records
- Payment records: meal / room rent / other
- Admin expense entry: বাজার, Current, Wi-Fi, খালা, অন্যান্য
- Bazar rotation (প্রতি ২ দিন) ও Toilet cleaning rotation (প্রতি ১০ দিন); settings-এ start date সেট করা যায়; পরবর্তী দায়িত্বের নাম bold দেখায়
- Editable monthly meal charge, default room rent, meal rates, rotation intervals এবং fine amounts
- Firestore security rules starter file

## গুরুত্বপূর্ণ সীমাবদ্ধতা

এটি Firebase configuration-সহ Android Studio source project, **APK নয়** এবং এখানে Android SDK/Gradle দিয়ে build বা ফোনে পরীক্ষা করা হয়নি। এই সংস্করণকে production-ready বলা যাবে না। Phone OTP, monthly closing/carry-forward, milestone-based automatic fine posting, member-wise due engine, PDF/Excel export, admin-এর app-এর ভিতর থেকে নতুন account তৈরি/role পরিবর্তন, এবং rotation order custom edit UI এখনো সম্পূর্ণ নয়। Auto-fine ইচ্ছাকৃতভাবে নিজে থেকে post হয় না, যাতে ভুল বা duplicate জরিমানা না লাগে।

## 1. Firebase Console প্রস্তুতি

1. https://console.firebase.google.com/ এ `apon-house` project খুলুন।
2. Authentication → Get started → Sign-in method → Email/Password → Enable → Save।
3. Firestore Database → Create database করুন। Location নির্বাচন করে database তৈরি করুন।
4. Firestore → Rules-এ এই প্রজেক্টের `firestore.rules` ফাইলের সম্পূর্ণ content paste করে **Publish** করুন। Test Mode ব্যবহার করবেন না।

## 2. Owner account তৈরি (প্রথমবার)

প্রথম Owner-কে একবার Firebase Console থেকে bootstrap করতে হবে:

1. Authentication → Users → Add user চাপুন। Owner-এর নিজস্ব email ও strong password দিন।
2. তৈরি হওয়া user-এর UID কপি করুন।
3. Firestore Database → Data → Start collection: `members`
4. Document ID হিসেবে ওই UID দিন। নিচের fields যোগ করুন:
   - `uid` (string): একই UID
   - `displayName` (string): Owner-এর নাম
   - `email` (string): Owner-এর email
   - `role` (string): `owner`
   - `active` (boolean): `true`
5. এরপর অ্যাপে ওই email/password দিয়ে Login করুন।

## 3. বাকি ১৮ জন

- প্রত্যেক সদস্য অ্যাপের Sign up দিয়ে Email/Password account খুলবেন।
- account খুললেই Firestore `members/{uid}`-এ `role: pending` profile তৈরি হবে।
- Owner/Monthly Manager/Monitor-কে Firebase Console-এর Authentication user UID দেখে সংশ্লিষ্ট `members/{uid}` document-এর `role` `member` করে এবং `active` `true` করতে হবে।
- Admin role দিতে `monthly_manager` বা `monitor` ব্যবহার করুন। Monitor-ও Admin permission পাবে।
- Room rent আলাদা; প্রতিটি সদস্যের `roomRent` field পরে member document-এ আলাদা করে যোগ করা যায়। এই starter এখনো member-wise due হিসাব স্বয়ংক্রিয় করে না।

## 4. Android Studio-তে চালানো

1. ZIP extract করুন।
2. Android Studio → Open → extracted `AponHouseFirebase` folder নির্বাচন করুন।
3. Gradle sync শেষ হতে দিন। Android SDK Platform 35 এবং JDK 17 লাগবে।
4. Android phone-এ USB debugging চালু করে Run চাপুন।
5. একই Firebase project-এর approved account দিয়ে অন্য ফোনে Login করলে Firestore listener-এর মাধ্যমে নতুন record live update হবে।

## 5. Firestore collections

- `members/{uid}`: displayName, email, role, active, roomRent (optional)
- `dailyMeals/{autoId}`: uid, memberName, date, breakfast, afternoon, dinner, amount
- `guestMeals/{autoId}`: uid, memberName, guestName, count, amount, date
- `payments/{autoId}`: uid, memberName, category, amount, date, note
- `expenses/{autoId}`: category, amount, note, date, createdBy
- `settings/current`: monthlyMealCharge, roomRentDefault, breakfastRate, afternoonRate, dinnerRate, fineBy5, fineBy10, fineBy15, cleaningIntervalDays, bazarIntervalDays, cleaningStartDate, bazarStartDate

## নিরাপত্তা

- `google-services.json` Android client configuration; এটি app source-এ থাকা স্বাভাবিক। তবে Firebase Console-এ API key restrictions ও App Check পরে configure করা ভালো।
- Firebase Admin SDK service-account private key কখনো APK, ZIP, chat, GitHub বা Android source-এ রাখবেন না।
- Owner bootstrap-এর পরে Firestore rules publish করে তবেই অন্য account ব্যবহার করুন।
