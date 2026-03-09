.PHONY: build build-release keystore sign all install clean

OUTPUT_DIR := output
APK_DEBUG := $(OUTPUT_DIR)/ketchup-debug.apk
APK_RELEASE_UNSIGNED := $(OUTPUT_DIR)/ketchup-release-unsigned.apk
APK_SIGNED := $(OUTPUT_DIR)/ketchup-signed.apk

build:
	mkdir -p $(OUTPUT_DIR)
	docker compose run --rm builder
	cp app/build/outputs/apk/debug/app-debug.apk $(APK_DEBUG)
	@echo "Debug APK: $(APK_DEBUG)"

build-release:
	mkdir -p $(OUTPUT_DIR)
	docker compose run --rm builder-release
	cp app/build/outputs/apk/release/app-release-unsigned.apk $(APK_RELEASE_UNSIGNED)
	@echo "Release APK: $(APK_RELEASE_UNSIGNED)"

keystore:
	@test -f .env || (echo "Copy .env.example to .env and fill in values"; exit 1)
	mkdir -p keystore
	docker compose run --rm --entrypoint bash builder /workspace/scripts/keystore.sh

sign:
	@test -f .env || (echo "Copy .env.example to .env and fill in values"; exit 1)
	docker compose run --rm --entrypoint bash builder /workspace/scripts/sign.sh

all: build-release sign

install:
	adb install -r $(APK_DEBUG)

clean:
	rm -rf $(OUTPUT_DIR)
	docker compose run --rm builder gradle clean
