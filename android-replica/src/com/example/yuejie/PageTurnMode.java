package com.example.yuejie;
/** Stable preference values; enum order is presentation order only. */
enum PageTurnMode {
 SIMULATION("simulation","仿真"),COVER("cover","覆盖"),SLIDE("slide","平移"),NONE("none","无动画");
 final String key,label;PageTurnMode(String key,String label){this.key=key;this.label=label;}
 static PageTurnMode parse(String value){for(PageTurnMode m:values())if(m.key.equals(value))return m;return COVER;}
}
