# Third-party notices

The root `LICENSE` applies to original NoteShadow code and documentation. It does not change the terms for the third-party components and assets listed here. An applicable open model license can authorize redistribution without separate permission; verify that it covers the exact converted files and follow its notice and attribution conditions before including them in an APK or source archive.

This public source repository excludes speech-model weights, their token files, and test audio. The model entries below document compatible components used during private development; the entries themselves are not licenses.

## sherpa-onnx 1.13.4

- Project: https://github.com/k2-fsa/sherpa-onnx
- License: Apache License 2.0; the full license text is included at `THIRD_PARTY_LICENSES/sherpa-onnx-Apache-2.0.txt`.
- Included component: Android ARM64 `libsherpa-onnx-jni.so` and Java API source files.

## Chinese streaming Zipformer CTC INT8 model

- Model: `sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01`
- Official documentation: https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-ctc/zipformer-ctc-models.html
- Upstream model repository: https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01
- The [ONNX conversion card](https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01) does not declare license metadata, while its [linked PyTorch checkpoint](https://huggingface.co/csukuangfj/icefall-streaming-zipformer-small-ctc-zh-2025-04-01) declares Apache-2.0. Confirm that the license applies to the converted ONNX file and include the required notices if distributing it.

## SenseVoice Small INT8 model

- Model: `sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09`
- Official documentation: https://k2-fsa.github.io/sherpa/onnx/sense-voice/pretrained.html
- Download archive SHA-256: `7305F7905BFCF77FA0B39388A313F3DA35C68D971661A65475B56FB2162C8E63`
- The private development build used `model.int8.onnx` and `tokens.txt`; this public repository does not include them. The model supports Chinese, English, Japanese, Korean and Cantonese.
- The [ONNX conversion card](https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09) has no license metadata and links to [WSYue-ASR](https://huggingface.co/ASLP-lab/WSYue-ASR), which declares Apache-2.0. The underlying [official SenseVoiceSmall model](https://huggingface.co/FunAudioLLM/SenseVoiceSmall) has its own model license. Verify which terms cover the converted files and preserve all required notices before distributing them.

## Silero VAD

- Project: https://github.com/snakers4/silero-vad
- The private development build used `silero_vad.onnx` from the sherpa-onnx ASR model release; this public repository does not include it.
- License: MIT.
