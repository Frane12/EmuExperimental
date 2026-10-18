package com.drnas.console;

import java.util.regex.*;

/** Keep A6/A7 upstream payloads separate from experimental per-chip A8 builds. */
final class TurnipDevicePolicy {
    static final String LEGACY="A6XX_A7XX";
    private static final Pattern ADRENO=Pattern.compile(
            "(?i)adreno\\s*(?:\\(tm\\))?[^0-9]{0,12}(610|612|615|616|618|619|620|630|640|642|643|644|650|660|662|663|680|690|702|710|720|722|725|730|732|735|740|750|810|830|840)(?![0-9])");
    static String target(String name) {
        if(name==null)return "";
        Matcher match=ADRENO.matcher(name);
        return match.find()?"A"+match.group(1):"";
    }
    static boolean legacy(String target) {
        return target!=null && target.matches("A[67][0-9]{2}") &&
                target.equals(target("Adreno "+target.substring(1)));
    }
    static boolean matches(String bundle,String actual) {
        return bundle.equals(actual) || LEGACY.equals(bundle)&&legacy(actual);
    }
    private TurnipDevicePolicy(){}
}
