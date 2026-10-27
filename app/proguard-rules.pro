# kotlinx.serialization keeps its own generated serializers via the plugin's consumer rules.
# ML Kit and CameraX ship consumer rules.

# Readable crash traces: keep file names and line numbers. Class and method names stay obfuscated;
# decode them with the release's mapping file (a release asset, see release.yml):
#   retrace inkstash-<version>-mapping.txt trace.txt
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
