//package com.example.demo.word;
//
//import org.json.JSONArray;
//import org.json.JSONObject;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.client.RestTemplate;
//
//import java.net.URLEncoder;
//import java.nio.charset.StandardCharsets;
//import java.text.Normalizer;
//import java.util.Map;
//import java.util.regex.Pattern;
//
//public class NominatimCityFinder {
//
//    public String getCityFromNominatim(String cityName) {
//        String correctedCityName = correctCityName(cityName);
//        String url = "https://nominatim.openstreetmap.org/search?q=" + URLEncoder.encode(correctedCityName, StandardCharsets.UTF_8) + "&format=json&limit=5";
//
//        RestTemplate restTemplate = new RestTemplate();
//        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
//
//        if (response.getStatusCode() == HttpStatus.OK) {
//            JSONArray jsonArray = new JSONArray(response.getBody());
//
//            for (int i = 0; i < jsonArray.length(); i++) {
//                JSONObject obj = jsonArray.getJSONObject(i);
//                if ("place".equals(obj.optString("class"))) {
//                    return obj.getString("display_name");
//                }
//            }
//        }
//
//        return "Місто не знайдено";
//    }
//
//    private String correctCityName(String cityName) {
//        Map<String, String> cityCorrections = Map.of(
//                "Zhuravlev", "Журавлёв",
//                "Chernyshëvka", "Чернышёвка"
//        );
//
//        return cityCorrections.getOrDefault(cityName, normalizeCityName(cityName));
//    }
//
//    private String normalizeCityName(String cityName) {
//        String temp = Normalizer.normalize(cityName, Normalizer.Form.NFD);
//        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
//        return pattern.matcher(temp).replaceAll("");
//    }
//}
