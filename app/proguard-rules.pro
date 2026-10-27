# kotlinx.serialization keeps its own generated serializers via the plugin's consumer rules.
# ML Kit and CameraX ship consumer rules.
# ML Kit finds its components by calling each registrar's no-arg constructor by reflection. The consumer rule
# from firebase-components only keeps the class, so R8 full mode removes the constructor and ML Kit never
# registers its scanners (BarcodeScanning.getClient then fails with a NullPointerException).
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
