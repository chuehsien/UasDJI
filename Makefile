
APKNAME=UasDJI
SVNVERSION=0
APK=UasDJI/build/$(APKNAME)-$(SVNVERSION).apk

#include ../common.mk

$(APK):
	@if [ ! -z "$(OBFUSCATE)" ]; then \
		gradle assembleRelease; \
		cp UasDJI/build/outputs/apk/$(APKNAME)-release.apk $(APK); \
                cp UasDJI/build/intermediates/transforms/dex/release/folders/1000/1f/main/classes.dex UasDJI/build/tmp.dex; \
	fi

	@if [ -z "$(OBFUSCATE)" ]; then \
		gradle assembleDebug; \
		cp UasDJI/build/outputs/apk/$(APKNAME)-debug.apk $(APK); \
                cp UasDJI/build/intermediates/transforms/dex/debug/folders/1000/1f/main/classes.dex UasDJI/build/tmp.dex; \
	fi

	adb install -r $(APK)

	@echo "=================================="
	@echo "APK generated: $@ "
	-@echo "dex limit reminder: `cat UasDJI/build/tmp.dex | head -c 92 | tail -c 4 | hexdump -e '1/4 \"%d\n\"'`"
	@echo "=================================="

all:	devrelease
	

clean:
	rm -fr $(APK)


devrelease: $(APK)

