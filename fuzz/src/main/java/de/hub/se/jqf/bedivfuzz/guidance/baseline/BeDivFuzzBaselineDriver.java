package de.hub.se.jqf.bedivfuzz.guidance.baseline;

import java.io.File;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Random;

import edu.berkeley.cs.jqf.fuzz.ei.ZestGuidance;
import edu.berkeley.cs.jqf.fuzz.guidance.GuidanceException;
import edu.berkeley.cs.jqf.fuzz.junit.GuidedFuzzing;
import org.junit.runner.Result;

/**
 * Entry point for fuzzing with baseline BeDivFuzz. Adapted from {@link ZestGuidance}
 *
 */
public class BeDivFuzzBaselineDriver {

    public static void main(String[] args) {
        if (args.length < 2){
            System.err.println("Usage: java " + BeDivFuzzBaselineDriver.class + " TEST_CLASS TEST_METHOD [OUTPUT_DIR]");
            System.exit(1);
        }

        String testClassName  = args[0];
        String testMethodName = args[1];
        String outputDirectoryName = args.length > 2 ? args[2] : "fuzz-results";
        File outputDirectory = new File(outputDirectoryName);
        File[] seedFiles = null;
        if (args.length > 3) {
            seedFiles = new File[args.length-3];
            for (int i = 3; i < args.length; i++) {
                seedFiles[i-3] = new File(args[i]);
            }
        }

        try {
            // Load the guidance
            String title = testClassName+"#"+testMethodName;
            Random rnd = new Random(); // TODO: Support deterministic PRNG
            BeDivFuzzBaselineGuidance guidance;

            // Try to parse campaign timeout
            String campaignTimeout = System.getProperty("jqf.guidance.campaign_timeout");
            Duration duration = null;
            if (campaignTimeout != null && !campaignTimeout.isEmpty()) {
                try {
                    duration = Duration.parse("PT"+campaignTimeout);
                } catch (DateTimeParseException e) {
                    throw new GuidanceException("Invalid time duration: " + campaignTimeout);
                }
            }

            // Disable havoc mutations
            System.setProperty("jqf.guidance.bedivfuzz.havoc_rate", "0.0");

            if (seedFiles == null) {
                guidance = new BeDivFuzzBaselineGuidance(title, duration, null, outputDirectory, rnd);
            } else if (seedFiles.length == 1 && seedFiles[0].isDirectory()) {
                guidance = new BeDivFuzzBaselineGuidance(title, duration, null, outputDirectory, seedFiles[0], rnd);
            } else {
                guidance = new BeDivFuzzBaselineGuidance(title, duration, null, outputDirectory, seedFiles, rnd);
            }

            Locale.setDefault(Locale.US);

            // Run the Junit test
            Result res = GuidedFuzzing.run(testClassName, testMethodName, guidance, System.out);
            if (Boolean.getBoolean("jqf.logCoverage")) {
                System.out.println(String.format("Covered %d edges.",
                        guidance.getTotalCoverage().getNonZeroCount()));
            }
            if (Boolean.getBoolean("jqf.ei.EXIT_ON_CRASH") && !res.wasSuccessful()) {
                System.exit(3);
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(2);
        }

    }
}

