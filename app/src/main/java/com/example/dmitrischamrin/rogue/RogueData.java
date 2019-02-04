package com.example.dmitrischamrin.rogue;

import android.content.Context;
import android.graphics.Color;
import android.support.v7.widget.RecyclerView;

import java.util.ArrayList;

public class RogueData {

    /**
     * Interface all ConfigItems must implement so the {@link RecyclerView}'s Adapter associated
     * with the configuration activity knows what type of ViewHolder to inflate.
     */
    public interface ConfigItemType {
        int getConfigType();
    }

    /**
     * Returns Watch Face Service class associated with configuration Activity.
     */
    public static Class getWatchFaceServiceClass() {
        return Rogue.class;
    }

    /**
     * Returns Material Design color options.
     */
    public static ArrayList<Integer> getColorOptionsDataSet() {
        ArrayList<Integer> colorOptionsDataSet = new ArrayList<>();

        colorOptionsDataSet.add(Color.parseColor("#06d388")); // Original
        colorOptionsDataSet.add(Color.parseColor("#70e266")); // Green
        colorOptionsDataSet.add(Color.parseColor("#3d54ff")); // Blue

        colorOptionsDataSet.add(Color.parseColor("#ff0000")); // Red
        colorOptionsDataSet.add(Color.parseColor("#ffd700")); // Gold
        colorOptionsDataSet.add(Color.parseColor("#39ff14")); // neongrün

        colorOptionsDataSet.add(Color.parseColor("#ff00ff")); // Magenta
        colorOptionsDataSet.add(Color.parseColor("#14caff")); // neonblau

        colorOptionsDataSet.add(Color.parseColor("#00FFFF")); // helltürkis
        colorOptionsDataSet.add(Color.parseColor("#ef461c")); // orange
        colorOptionsDataSet.add(Color.parseColor("#8325f7")); // lila
        colorOptionsDataSet.add(Color.parseColor("#ffff00")); // gelb
        colorOptionsDataSet.add(Color.parseColor("#04B404")); // dunkelgrün



        return colorOptionsDataSet;
    }

    /**
     * Includes all data to populate each of the 5 different custom
     * {@link RecyclerView.ViewHolder} types in {@link RogueComplicationConfigRecyclerViewAdapter}.
     */
    public static ArrayList<ConfigItemType> getDataToPopulateAdapter(Context context) {

        ArrayList<ConfigItemType> settingsConfigData = new ArrayList<>();

        // Data for watch face preview and complications UX in settings Activity.
        ConfigItemType complicationConfigItem =
                new PreviewAndComplicationsConfigItem(R.drawable.add_complication);
        settingsConfigData.add(complicationConfigItem);

        // Data for "more options" UX in settings Activity.
        ConfigItemType moreOptionsConfigItem =
                new MoreOptionsConfigItem(R.drawable.ic_expand_more_white_18dp);
        settingsConfigData.add(moreOptionsConfigItem);

        // Data for highlight/marker (second hand) color UX in settings Activity.
        ConfigItemType markerColorConfigItem =
                new ColorConfigItem(
                        context.getString(R.string.config_marker_color_label),
                        R.drawable.icn_styles,
                        context.getString(R.string.saved_marker_color),
                        ColorSelectionActivity.class);
        settingsConfigData.add(markerColorConfigItem);

        // Data for Background color UX in settings Activity.
//        ConfigItemType backgroundColorConfigItem =
//                new ColorConfigItem(
//                        context.getString(R.string.config_background_color_label),
//                        R.drawable.icn_styles,
//                        context.getString(R.string.saved_background_color),
//                        ColorSelectionActivity.class);
//        settingsConfigData.add(backgroundColorConfigItem);

        // Data for 'Unread Notifications' UX (toggle) in settings Activity.
        ConfigItemType unreadNotificationsConfigItem =
                new UnreadNotificationConfigItem(
                        context.getString(R.string.config_unread_notifications_label),
                        R.drawable.ic_blur_on,
                        R.drawable.ic_blur_off,
                        R.string.saved_unread_notifications_pref);
        settingsConfigData.add(unreadNotificationsConfigItem);

        // Data for background complications UX in settings Activity.
//        ConfigItemType backgroundImageComplicationConfigItem =
//                // TODO (jewalker): Revised in another CL to support background complication.
//                new BackgroundComplicationConfigItem(
//                        context.getString(R.string.config_background_image_complication_label),
//                        R.drawable.ic_landscape_white);
//        settingsConfigData.add(backgroundImageComplicationConfigItem);

        return settingsConfigData;
    }

    /**
     * Data for Watch Face Preview with Complications Preview item in RecyclerView.
     */
    public static class PreviewAndComplicationsConfigItem implements ConfigItemType {

        private int defaultComplicationResourceId;

        PreviewAndComplicationsConfigItem(int defaultComplicationResourceId) {
            this.defaultComplicationResourceId = defaultComplicationResourceId;
        }

        public int getDefaultComplicationResourceId() {
            return defaultComplicationResourceId;
        }

        @Override
        public int getConfigType() {
            return RogueComplicationConfigRecyclerViewAdapter.TYPE_PREVIEW_AND_COMPLICATIONS_CONFIG;
        }
    }

    /**
     * Data for "more options" item in RecyclerView.
     */
    public static class MoreOptionsConfigItem implements ConfigItemType {

        private int iconResourceId;

        MoreOptionsConfigItem(int iconResourceId) {
            this.iconResourceId = iconResourceId;
        }

        public int getIconResourceId() {
            return iconResourceId;
        }

        @Override
        public int getConfigType() {
            return RogueComplicationConfigRecyclerViewAdapter.TYPE_MORE_OPTIONS;
        }
    }

    /**
     * Data for color picker item in RecyclerView.
     */
    public static class ColorConfigItem  implements ConfigItemType {

        private String name;
        private int iconResourceId;
        private String sharedPrefString;
        private Class<ColorSelectionActivity> activityToChoosePreference;

        ColorConfigItem(
                String name,
                int iconResourceId,
                String sharedPrefString,
                Class<ColorSelectionActivity> activity) {
            this.name = name;
            this.iconResourceId = iconResourceId;
            this.sharedPrefString = sharedPrefString;
            this.activityToChoosePreference = activity;
        }

        public String getName() {
            return name;
        }

        public int getIconResourceId() {
            return iconResourceId;
        }

        public String getSharedPrefString() {
            return sharedPrefString;
        }

        public Class<ColorSelectionActivity> getActivityToChoosePreference() {
            return activityToChoosePreference;
        }

        @Override
        public int getConfigType() {
            return RogueComplicationConfigRecyclerViewAdapter.TYPE_COLOR_CONFIG;
        }
    }

    /**
     * Data for Unread Notification preference picker item in RecyclerView.
     */
    public static class UnreadNotificationConfigItem  implements ConfigItemType {

        private String name;
        private int iconEnabledResourceId;
        private int iconDisabledResourceId;
        private int sharedPrefId;

        UnreadNotificationConfigItem(
                String name,
                int iconEnabledResourceId,
                int iconDisabledResourceId,
                int sharedPrefId) {
            this.name = name;
            this.iconEnabledResourceId = iconEnabledResourceId;
            this.iconDisabledResourceId = iconDisabledResourceId;
            this.sharedPrefId = sharedPrefId;
        }

        public String getName() {
            return name;
        }

        public int getIconEnabledResourceId() {
            return iconEnabledResourceId;
        }

        public int getIconDisabledResourceId() {
            return iconDisabledResourceId;
        }

        public int getSharedPrefId() {
            return sharedPrefId;
        }

        @Override
        public int getConfigType() {
            return RogueComplicationConfigRecyclerViewAdapter.TYPE_UNREAD_NOTIFICATION_CONFIG;
        }
    }

    /**
     * Data for background image complication picker item in RecyclerView.
     */
    public static class BackgroundComplicationConfigItem  implements ConfigItemType {

        private String name;
        private int iconResourceId;

        BackgroundComplicationConfigItem(
                String name,
                int iconResourceId) {

            this.name = name;
            this.iconResourceId = iconResourceId;
        }

        public String getName() {
            return name;
        }

        public int getIconResourceId() {
            return iconResourceId;
        }

        @Override
        public int getConfigType() {
            return RogueComplicationConfigRecyclerViewAdapter.TYPE_BACKGROUND_COMPLICATION_IMAGE_CONFIG;
        }
    }
}
