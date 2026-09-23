# Third-party notices

The root `LICENSE` applies to original NoteShadow code and documentation. It does not change the terms for the third-party components and assets listed here. Model redistribution terms marked as unverified must be resolved before publicly distributing a source archive or APK that includes those files.

This public source repository excludes speech-model weights, their token files, and test audio. The model entries below document compatible components used during private development; a notice is not a grant of permission to redistribute a model.

## sherpa-onnx 1.13.4

- Project: https://github.com/k2-fsa/sherpa-onnx
- License: Apache License 2.0; the full license text is included at `THIRD_PARTY_LICENSES/sherpa-onnx-Apache-2.0.txt`.
- Included component: Android ARM64 `libsherpa-onnx-jni.so` and Java API source files.

## Chinese streaming Zipformer CTC INT8 model

- Model: `sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01`
- Official documentation: https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-ctc/zipformer-ctc-models.html
- Upstream model repository: https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01
- The upstream model card does not declare separate YAML license metadata. Model files are not included here; confirm redistribution terms before distributing them separately or in an APK.

## SenseVoice Small INT8 model

- Model: `sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09`
- Official documentation: https://k2-fsa.github.io/sherpa/onnx/sense-voice/pretrained.html
- Download archive SHA-256: `7305F7905BFCF77FA0B39388A313F3DA35C68D971661A65475B56FB2162C8E63`
- The private development build used `model.int8.onnx` and `tokens.txt`; this public repository does not include them. The model supports Chinese, English, Japanese, Korean and Cantonese.
- Confirm the upstream model redistribution terms before distributing its files separately or in an APK.

## Silero VAD

- Project: https://github.com/snakers4/silero-vad
- The private development build used `silero_vad.onnx` from the sherpa-onnx ASR model release; this public repository does not include it.
- License: MIT.
