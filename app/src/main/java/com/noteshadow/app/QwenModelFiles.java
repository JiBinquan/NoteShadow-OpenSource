package com.noteshadow.app;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Installs bundled Qwen files on first use while preserving an existing user-installed model. */
final class QwenModelFiles {
    private static final String[] PATHS = {
            "conv_frontend.onnx", "encoder.int8.onnx", "decoder.int8.onnx",
            "tokenizer/vocab.json", "tokenizer/merges.txt", "tokenizer/tokenizer_config.json"
    };
    private static final long[] MINIMUM_BYTES = {
            40_000_000L, 170_000_000L, 700_000_000L, 2_000_000L, 1_000_000L, 100L
    };

    private QwenModelFiles() {}

    static synchronized File require(Context context) throws IOException {
        File external = context.getExternalFilesDir(null);
        if (external == null) throw new IOException("无法访问应用模型目录");
        File directory = new File(external, "models/qwen3-int8");
        for (int i = 0; i < PATHS.length; i++) {
            File target = new File(directory, PATHS[i]);
            if (target.isFile() && target.length() >= MINIMUM_BYTES[i]) continue;
            installAsset(context, PATHS[i], target, MINIMUM_BYTES[i]);
        }
        return directory;
    }

    private static void installAsset(Context context, String path, File target, long minimumBytes)
            throws IOException {
        File parent = target.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs())
            throw new IOException("无法创建 Qwen 模型目录：" + parent);
        File temporary = new File(parent, target.getName() + ".tmp");
        try {
            try (InputStream in = context.getAssets().open("qwen3-int8/" + path);
                 FileOutputStream out = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[128 * 1024];
                int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                out.getFD().sync();
            }
            if (temporary.length() < minimumBytes)
                throw new IOException("Qwen 模型文件不完整：" + path);
            if (target.exists() && !target.delete())
                throw new IOException("无法更新 Qwen 模型文件：" + path);
            if (!temporary.renameTo(target))
                throw new IOException("无法安装 Qwen 模型文件：" + path);
        } catch (IOException e) {
            if (temporary.exists()) temporary.delete();
            if (e instanceof java.io.FileNotFoundException)
                throw new IOException("Qwen 模型未安装完整：" + path, e);
            throw e;
        }
    }
}
