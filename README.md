# 📱 أكاديمية أويلر للعلوم الدقيقة — تطبيق أندرويد (`Euler Academy Android`)

<div align="center">

[![Android APK](https://img.shields.io/badge/Android-APK%20Release-10b981?style=for-the-badge&logo=android)](https://github.com/eulerx0/euler-academy-android/releases/latest)
[![Offline First](https://img.shields.io/badge/Offline-100%25%20Built--in-06b6d4?style=for-the-badge)](https://github.com/eulerx0/euler-academy-android/releases/latest)
[![2BAC PC](https://img.shields.io/badge/2BAC%20PC-Maroc-3b82f6?style=for-the-badge)](https://eulerx0.github.io/euler-academy/)

**التطبيق الرسمي لأكاديمية أويلر على هواتف Android — مصمم 100% للهاتف ويعمل بدون إنترنت مع تحديث تلقائي للدروس**

📥 **[تحميل أحدث نسخة APK مباشرة](https://github.com/eulerx0/euler-academy-android/releases/latest/download/Euler-Academy.apk)**

</div>

---

## ✨ مميزات التطبيق

- **📶 100% بدون إنترنت (Offline-First):** جميع الدروس، محرك `KaTeX` للرياضيات، خطوط `Cairo`/`Inter`، `Tailwind CSS`، والمختبرات البصرية `60fps` مدمجة داخل التطبيق من أول تثبيت عبر خادم أصول محلي (`LocalAssetEngine`) دون أي مشاكل `file://`.
- **🔄 تحديث تلقائي ذكي للدروس (`OTA Sync`):** عند فتح التطبيق مع وجود اتصال بالإنترنت، يتحقق بصمت من `manifest.json` في [euler-academy](https://github.com/eulerx0/euler-academy) ويحمّل الملفات المحدّثة فقط في ثوانٍ دون الحاجة لإعادة تثبيت الـ `APK`.
- **📱 واجهة مخصصة للهاتف 100%:**
  - شريط تنقل سفلي أصلي (`Material 3 Bottom Navigation`) لسهولة الوصول بيد واحدة إلى **الرياضيات**، **الفيزياء**، **الكيمياء**، و**زر التحديث**.
  - دعم كامل لزر الرجوع في أندرويد للتنقل السلس بين الدروس والفهرس.
  - تصدير أي درس إلى **PDF للطباعة** مباشرة عبر خدمة الطباعة الأصلية في أندرويد (`PrintManager`).
  - ثيم داكن متناسق مع شريط الحالة وشريط التنقل (`#060911`).
- **🚀 بناء وإصدار آلي (`GitHub Actions CI/CD`):** يُعاد بناء ملف `Euler-Academy.apk` الموقّع رقمياً تلقائياً عند كل تحديث جديد في المنهاج ويُتاح داخل التطبيق عبر نظام التنبيه بالإصدارات الجديدة.

---

## 🏗️ بنية المشروع

```text
euler-academy-android/
├── .github/workflows/build-apk.yml   # البناء والتوقيع والنشر التلقائي على GitHub Releases
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/ma/euleracademy/bac2pc/
│   │   ├── MainActivity.kt           # واجهة التطبيق، التنقل السفلي، زر الرجوع، والطباعة
│   │   ├── LocalAssetEngine.kt       # محرك الأصول المحلية (OTA Priority + Bundled Fallback)
│   │   └── OtaSyncManager.kt         # مزامن الدروس SHA-256 OTA + محدث APK
│   └── res/                          # الأيقونة المتجهية ∑، الألوان، والقوائم RTL
└── build.gradle.kts
```
