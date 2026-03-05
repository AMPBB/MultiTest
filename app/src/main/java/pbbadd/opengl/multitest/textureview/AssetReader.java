package pbbadd.opengl.multitest.textureview;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class AssetReader {
    private static final String tag = "AssetReader";
    private final Context mContext;

    public AssetReader(Context context) {
        mContext = context.getApplicationContext();
    }

    public String readTextFromAssets(String assetPath) {
        StringBuilder content = new StringBuilder();
        AssetManager assetManager = mContext.getAssets();
        try (
                InputStream is = assetManager.open(assetPath);
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr)
        ) {
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            Log.e(tag, "assets open failed," + assetPath, e);
            return null;
        }
        return content.toString();
    }
}
