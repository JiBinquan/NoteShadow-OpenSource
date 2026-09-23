# Third-party notices

The root `LICENSE` applies to original NoteShadow code and documentation. It does not change the terms for the third-party components and assets listed here. For the preview APK, the license assessment below follows the converted models' declared source lineage and includes the applicable source license texts and attribution. The ONNX conversion cards do not themselves declare separate license metadata; this lineage assessment is an inference from the cited upstream pages, not an explicit license statement on those cards.

This public source repository excludes speech-model weights, their token files, and test audio. The model entries below document compatible components used during private development; the entries themselves are not licenses.

The full APK release includes model files under the cited licenses. Its `assets/licenses/` directory contains the original license texts and an attribution manifest. The no-model APK contains the same notices but no speech-model weights or vocabularies. The self-test audio is not distributed.

## sherpa-onnx 1.13.4

- Project: https://github.com/k2-fsa/sherpa-onnx
- License: Apache License 2.0; the full license text is included at `THIRD_PARTY_LICENSES/sherpa-onnx-Apache-2.0.txt`.
- Included component: Android ARM64 `libsherpa-onnx-jni.so` and Java API source files.

## Chinese streaming Zipformer CTC INT8 model

- Model: `sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01`
- Official documentation: https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-ctc/zipformer-ctc-models.html
- Upstream model repository: https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01
- The [ONNX conversion card](https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01) links to the same uploader's [PyTorch checkpoint](https://huggingface.co/csukuangfj/icefall-streaming-zipformer-small-ctc-zh-2025-04-01), which declares Apache-2.0. We apply that source license to the converted files and include its text and attribution. The conversion card has no separate license metadata.

## SenseVoice Small INT8 model

- Model: `sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09`
- Official documentation: https://k2-fsa.github.io/sherpa/onnx/sense-voice/pretrained.html
- Download archive SHA-256: `7305F7905BFCF77FA0B39388A313F3DA35C68D971661A65475B56FB2162C8E63`
- The private development build used `model.int8.onnx` and `tokens.txt`; this public repository does not include them. The model supports Chinese, English, Japanese, Korean and Cantonese.
- The [ONNX conversion card](https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09) identifies [WSYue-ASR](https://huggingface.co/ASLP-lab/WSYue-ASR) as its source; that repository declares Apache-2.0. The underlying [official SenseVoiceSmall model](https://huggingface.co/FunAudioLLM/SenseVoiceSmall) follows the [FunASR Model Open Source License Agreement v1.1](https://github.com/modelscope/FunASR/blob/main/MODEL_LICENSE), which permits sharing with source, author, and model-name attribution. We include both license texts and those attributions for this conversion. The conversion card has no separate license metadata.

## Silero VAD

- Project: https://github.com/snakers4/silero-vad
- The private development build used `silero_vad.onnx` from the sherpa-onnx ASR model release; this public repository does not include it.
- License: MIT.
