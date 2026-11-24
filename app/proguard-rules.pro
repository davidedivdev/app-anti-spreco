# Regole R8 per la build di rilascio.
# Le librerie (Room, WorkManager, ML Kit, AdMob, Billing) portano già le loro regole;
# queste sono solo una rete di sicurezza per le classi dell'app usate per nome.
-keep class com.davide.salvacibo.data.Product { *; }
-keep class com.davide.salvacibo.notifications.** { *; }
-keep class com.davide.salvacibo.SalvaciboApp { *; }

# Messaggi di stack trace leggibili nei report di crash di Play Console
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
