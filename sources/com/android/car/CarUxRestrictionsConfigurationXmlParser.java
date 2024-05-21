package com.android.car;

import android.car.drivingstate.CarUxRestrictionsConfiguration;
import android.content.Context;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Slog;
import android.util.Xml;
import androidx.core.app.FrameMetricsAggregator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.xmlpull.v1.XmlPullParserException;
/* loaded from: classes3.dex */
public final class CarUxRestrictionsConfigurationXmlParser {
    private static final String CONTENT_RESTRICTIONS = "ContentRestrictions";
    private static final String DRIVING_STATE = "DrivingState";
    private static final float INVALID_SPEED = -1.0f;
    private static final String RESTRICTIONS = "Restrictions";
    private static final String RESTRICTION_MAPPING = "RestrictionMapping";
    private static final String RESTRICTION_PARAMETERS = "RestrictionParameters";
    private static final String ROOT_ELEMENT = "UxRestrictions";
    private static final String STRING_RESTRICTIONS = "StringRestrictions";
    private static final String TAG = "UxRConfigParser";
    private static final int UX_RESTRICTIONS_UNKNOWN = -1;
    private final Context mContext;
    private int mMaxRestrictedStringLength = -1;
    private int mMaxCumulativeContentItems = -1;
    private int mMaxContentDepth = -1;
    private final List<CarUxRestrictionsConfiguration.Builder> mConfigBuilders = new ArrayList();

    private CarUxRestrictionsConfigurationXmlParser(Context context) {
        this.mContext = context;
    }

    public static List<CarUxRestrictionsConfiguration> parse(Context context, int xmlResource) throws IOException, XmlPullParserException {
        return new CarUxRestrictionsConfigurationXmlParser(context).parse(xmlResource);
    }

    private List<CarUxRestrictionsConfiguration> parse(int xmlResource) throws IOException, XmlPullParserException {
        XmlResourceParser parser = this.mContext.getResources().getXml(xmlResource);
        if (parser == null) {
            Slog.e(TAG, "Invalid Xml resource");
            return null;
        } else if (!traverseUntilStartTag(parser)) {
            Slog.e(TAG, "XML root element invalid: " + parser.getName());
            return null;
        } else if (!traverseUntilEndOfDocument(parser)) {
            Slog.e(TAG, "Could not parse XML to end");
            return null;
        } else {
            List<CarUxRestrictionsConfiguration> configs = new ArrayList<>();
            for (CarUxRestrictionsConfiguration.Builder builder : this.mConfigBuilders) {
                builder.setMaxStringLength(this.mMaxRestrictedStringLength).setMaxCumulativeContentItems(this.mMaxCumulativeContentItems).setMaxContentDepth(this.mMaxContentDepth);
                configs.add(builder.build());
            }
            return configs;
        }
    }

    private boolean traverseUntilStartTag(XmlResourceParser parser) throws IOException, XmlPullParserException {
        int type;
        do {
            type = parser.next();
            if (type == 1) {
                break;
            }
        } while (type != 2);
        return ROOT_ELEMENT.equals(parser.getName());
    }

    private boolean traverseUntilEndOfDocument(XmlResourceParser parser) throws XmlPullParserException, IOException {
        AttributeSet attrs = Xml.asAttributeSet(parser);
        while (parser.getEventType() != 1) {
            if (parser.next() == 2) {
                String name = parser.getName();
                char c = 65535;
                int hashCode = name.hashCode();
                if (hashCode != 545370806) {
                    if (hashCode == 1664662402 && name.equals(RESTRICTION_MAPPING)) {
                        c = 0;
                    }
                } else if (name.equals(RESTRICTION_PARAMETERS)) {
                    c = 1;
                }
                if (c == 0) {
                    this.mConfigBuilders.add(new CarUxRestrictionsConfiguration.Builder());
                    if (!mapDrivingStateToRestrictions(parser, attrs)) {
                        Slog.e(TAG, "Could not map driving state to restriction.");
                        return false;
                    }
                } else if (c == 1) {
                    if (!parseRestrictionParameters(parser, attrs) && Log.isLoggable(TAG, 4)) {
                        Slog.i(TAG, "Error reading restrictions parameters. Falling back to platform defaults.");
                    }
                } else {
                    Slog.w(TAG, "Unknown class:" + parser.getName());
                }
            }
        }
        return true;
    }

    private boolean mapDrivingStateToRestrictions(XmlResourceParser parser, AttributeSet attrs) throws IOException, XmlPullParserException {
        if (parser == null || attrs == null) {
            Slog.e(TAG, "Invalid arguments");
            return false;
        } else if (!RESTRICTION_MAPPING.equals(parser.getName())) {
            Slog.e(TAG, "Parser not at RestrictionMapping element: " + parser.getName());
            return false;
        } else {
            TypedArray a = this.mContext.getResources().obtainAttributes(attrs, R.styleable.UxRestrictions_RestrictionMapping);
            if (a.hasValue(0)) {
                int portValue = a.getInt(0, 0);
                byte port = CarUxRestrictionsConfiguration.Builder.validatePort(portValue);
                getCurrentBuilder().setPhysicalPort(port);
            }
            a.recycle();
            if (!traverseToTag(parser, DRIVING_STATE)) {
                Slog.e(TAG, "No <DrivingState> tag in XML");
                return false;
            }
            while (DRIVING_STATE.equals(parser.getName())) {
                if (parser.getEventType() == 2) {
                    TypedArray a2 = this.mContext.getResources().obtainAttributes(attrs, R.styleable.UxRestrictions_DrivingState);
                    int drivingState = a2.getInt(2, -1);
                    float minSpeed = a2.getFloat(1, INVALID_SPEED);
                    float maxSpeed = a2.getFloat(0, Float.POSITIVE_INFINITY);
                    a2.recycle();
                    if (!traverseToTag(parser, RESTRICTIONS)) {
                        Slog.e(TAG, "No <Restrictions> tag in XML");
                        return false;
                    }
                    CarUxRestrictionsConfiguration.Builder.SpeedRange speedRange = parseSpeedRange(minSpeed, maxSpeed);
                    if (!parseAllRestrictions(parser, attrs, drivingState, speedRange)) {
                        Slog.e(TAG, "Could not parse restrictions for driving state:" + drivingState);
                        return false;
                    }
                }
                parser.next();
            }
            return true;
        }
    }

    private boolean parseAllRestrictions(XmlResourceParser parser, AttributeSet attrs, int drivingState, CarUxRestrictionsConfiguration.Builder.SpeedRange speedRange) throws IOException, XmlPullParserException {
        if (parser == null || attrs == null) {
            Slog.e(TAG, "Invalid arguments");
            return false;
        } else if (!RESTRICTIONS.equals(parser.getName())) {
            Slog.e(TAG, "Parser not at Restrictions element: " + parser.getName());
            return false;
        } else {
            while (RESTRICTIONS.equals(parser.getName())) {
                if (parser.getEventType() == 2) {
                    CarUxRestrictionsConfiguration.DrivingStateRestrictions restrictions = parseRestrictions(parser, attrs);
                    if (restrictions == null) {
                        Slog.e(TAG, "");
                        return false;
                    }
                    restrictions.setSpeedRange(speedRange);
                    if (Log.isLoggable(TAG, 3)) {
                        Slog.d(TAG, "Map " + drivingState + " : " + restrictions);
                    }
                    if (drivingState != -1) {
                        getCurrentBuilder().setUxRestrictions(drivingState, restrictions);
                    }
                }
                parser.next();
            }
            return true;
        }
    }

    private CarUxRestrictionsConfiguration.DrivingStateRestrictions parseRestrictions(XmlResourceParser parser, AttributeSet attrs) throws IOException, XmlPullParserException {
        if (parser == null || attrs == null) {
            Slog.e(TAG, "Invalid Arguments");
            return null;
        }
        int restrictions = -1;
        String restrictionMode = "baseline";
        boolean requiresOpt = true;
        while (RESTRICTIONS.equals(parser.getName()) && parser.getEventType() == 2) {
            TypedArray a = this.mContext.getResources().obtainAttributes(attrs, R.styleable.UxRestrictions_Restrictions);
            restrictions = a.getInt(2, FrameMetricsAggregator.EVERY_DURATION);
            requiresOpt = a.getBoolean(1, true);
            restrictionMode = a.getString(0);
            a.recycle();
            parser.next();
        }
        if (restrictionMode == null) {
            restrictionMode = "baseline";
        }
        return new CarUxRestrictionsConfiguration.DrivingStateRestrictions().setDistractionOptimizationRequired(requiresOpt).setRestrictions(restrictions).setMode(restrictionMode);
    }

    private CarUxRestrictionsConfiguration.Builder.SpeedRange parseSpeedRange(float minSpeed, float maxSpeed) {
        if (Float.compare(minSpeed, 0.0f) < 0 || Float.compare(maxSpeed, 0.0f) < 0) {
            return null;
        }
        return new CarUxRestrictionsConfiguration.Builder.SpeedRange(minSpeed, maxSpeed);
    }

    private boolean traverseToTag(XmlResourceParser parser, String tag) throws IOException, XmlPullParserException {
        if (tag == null || parser == null) {
            return false;
        }
        while (true) {
            int type = parser.next();
            if (type == 1) {
                return false;
            }
            if (type == 2 && parser.getName().equals(tag)) {
                return true;
            }
        }
    }

    /* JADX WARN: Removed duplicated region for block: B:32:0x0076  */
    /* JADX WARN: Removed duplicated region for block: B:37:0x00b0  */
    /* JADX WARN: Removed duplicated region for block: B:47:0x00c5 A[SYNTHETIC] */
    /* JADX WARN: Removed duplicated region for block: B:50:0x002e A[SYNTHETIC] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    private boolean parseRestrictionParameters(android.content.res.XmlResourceParser r12, android.util.AttributeSet r13) throws java.io.IOException, org.xmlpull.v1.XmlPullParserException {
        /*
            r11 = this;
            java.lang.String r0 = "UxRConfigParser"
            r1 = 0
            if (r12 == 0) goto Lcb
            if (r13 != 0) goto L9
            goto Lcb
        L9:
            java.lang.String r2 = r12.getName()
            java.lang.String r3 = "RestrictionParameters"
            boolean r2 = r3.equals(r2)
            if (r2 != 0) goto L2e
            java.lang.StringBuilder r2 = new java.lang.StringBuilder
            r2.<init>()
            java.lang.String r3 = "Parser not at RestrictionParameters element: "
            r2.append(r3)
            java.lang.String r3 = r12.getName()
            r2.append(r3)
            java.lang.String r2 = r2.toString()
            android.util.Slog.e(r0, r2)
            return r1
        L2e:
            int r2 = r12.getEventType()
            r4 = 1
            if (r2 == r4) goto Lca
            int r2 = r12.next()
            r5 = 3
            if (r2 != r5) goto L47
            java.lang.String r6 = r12.getName()
            boolean r6 = r3.equals(r6)
            if (r6 == 0) goto L47
            return r4
        L47:
            r6 = 2
            if (r2 != r6) goto Lc8
            r6 = 0
            java.lang.String r7 = r12.getName()
            int r8 = r7.hashCode()
            r9 = 1199884576(0x4784c920, float:67986.25)
            r10 = -1
            if (r8 == r9) goto L69
            r9 = 1603683576(0x5f9644f8, float:2.1656104E19)
            if (r8 == r9) goto L5f
        L5e:
            goto L73
        L5f:
            java.lang.String r8 = "StringRestrictions"
            boolean r7 = r7.equals(r8)
            if (r7 == 0) goto L5e
            r7 = r1
            goto L74
        L69:
            java.lang.String r8 = "ContentRestrictions"
            boolean r7 = r7.equals(r8)
            if (r7 == 0) goto L5e
            r7 = r4
            goto L74
        L73:
            r7 = r10
        L74:
            if (r7 == 0) goto Lb0
            if (r7 == r4) goto L97
            boolean r4 = android.util.Log.isLoggable(r0, r5)
            if (r4 == 0) goto Lc3
            java.lang.StringBuilder r4 = new java.lang.StringBuilder
            r4.<init>()
            java.lang.String r5 = "Unsupported Restriction Parameters in XML: "
            r4.append(r5)
            java.lang.String r5 = r12.getName()
            r4.append(r5)
            java.lang.String r4 = r4.toString()
            android.util.Slog.d(r0, r4)
            goto Lc3
        L97:
            android.content.Context r5 = r11.mContext
            android.content.res.Resources r5 = r5.getResources()
            int[] r7 = com.android.car.R.styleable.UxRestrictions_ContentRestrictions
            android.content.res.TypedArray r6 = r5.obtainAttributes(r13, r7)
            int r5 = r6.getInt(r1, r10)
            r11.mMaxCumulativeContentItems = r5
            int r4 = r6.getInt(r4, r10)
            r11.mMaxContentDepth = r4
            goto Lc3
        Lb0:
            android.content.Context r4 = r11.mContext
            android.content.res.Resources r4 = r4.getResources()
            int[] r5 = com.android.car.R.styleable.UxRestrictions_StringRestrictions
            android.content.res.TypedArray r6 = r4.obtainAttributes(r13, r5)
            int r4 = r6.getInt(r1, r10)
            r11.mMaxRestrictedStringLength = r4
        Lc3:
            if (r6 == 0) goto Lc8
            r6.recycle()
        Lc8:
            goto L2e
        Lca:
            return r4
        Lcb:
            java.lang.String r2 = "Invalid arguments"
            android.util.Slog.e(r0, r2)
            return r1
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.car.CarUxRestrictionsConfigurationXmlParser.parseRestrictionParameters(android.content.res.XmlResourceParser, android.util.AttributeSet):boolean");
    }

    private CarUxRestrictionsConfiguration.Builder getCurrentBuilder() {
        List<CarUxRestrictionsConfiguration.Builder> list = this.mConfigBuilders;
        return list.get(list.size() - 1);
    }
}
