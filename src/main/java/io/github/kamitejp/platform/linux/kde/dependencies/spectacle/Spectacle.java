package io.github.kamitejp.platform.linux.kde.dependencies.spectacle;

import io.github.kamitejp.geometry.Rectangle;
import io.github.kamitejp.image.ImageOps;
import io.github.kamitejp.platform.BaseSimpleDependency;
import io.github.kamitejp.platform.process.ProcessHelper;
import io.github.kamitejp.platform.process.ProcessRunParams;

import java.util.ArrayList;

public class Spectacle extends BaseSimpleDependency {
    public Spectacle() {
        super("spectacle");
    }

    @Override
    public boolean checkIsAvailable() {
        var res = ProcessHelper.run(
                ProcessRunParams.ofCmd(BIN, "-h").withTimeout(DEFAULT_AVAILABILITY_CHECK_TIMEOUT_MS)
        );
        return res.didComplete() && res.getStdout().startsWith("Usage: spectacle");
    }

    public SpectacleResult takeScreenshot(Rectangle area) {
        var cmd = new ArrayList<String>();
        cmd.add(BIN);
        cmd.add("-bno");
        cmd.add("/dev/stdout");

        var res = ProcessHelper.runWithBinaryOutput(ProcessRunParams.ofCmd(cmd).withTimeout(3000));
        if (!res.didComplete()) {
            return new SpectacleResult.ExecutionFailed();
        } else if (res.didCompleteWithError()) {
            return new SpectacleResult.Error(res.getStderr());
        }

        var image = ImageOps.fromBytes(res.getStdout());
        if (image == null) {
            return new SpectacleResult.ExecutionFailed();
        }

        if (area != null) {
          image = image.getSubimage(area.getLeft(), area.getTop(), area.getWidth(), area.getHeight());
        }

        return new SpectacleResult.Screenshot(image);
    }
}
