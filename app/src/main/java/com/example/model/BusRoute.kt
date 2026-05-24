package com.example.model

data class RouteStop(
    val englishName: String,
    val sinhalaName: String,
    val latitude: Double,
    val longitude: Double,
    val distanceKmOffset: Double
)

data class BusRoute(
    val routeNumber: String,
    val englishTitle: String,
    val sinhalaTitle: String,
    val startStationEnglish: String,
    val startStationSinhala: String,
    val endStationEnglish: String,
    val endStationSinhala: String,
    val category: String, // Normal, Semi-Luxury, AC-Expressway
    val baseFareLkr: Double,
    val perKmRateLkr: Double,
    val stops: List<RouteStop>
)

object BusRouteData {
    val sampleRoutes = listOf(
        BusRoute(
            routeNumber = "138",
            englishTitle = "Pettah - Maharagama",
            sinhalaTitle = "පිටකොටුව - මහරගම",
            startStationEnglish = "Pettah (Colombo)",
            startStationSinhala = "පිටකොටුව (කොළඹ)",
            endStationEnglish = "Maharagama",
            endStationSinhala = "මහරගම",
            category = "Normal",
            baseFareLkr = 30.0,
            perKmRateLkr = 9.5,
            stops = listOf(
                RouteStop("Pettah", "පිටකොටුව", 6.9344, 79.8518, 0.0),
                RouteStop("Eye Hospital", "ඇස් රෝහල", 6.9234, 79.8631, 2.2),
                RouteStop("Borella", "බොරැල්ල", 6.9205, 79.8781, 4.5),
                RouteStop("Kirulapone", "කිරුළපන", 6.8841, 79.8789, 8.5),
                RouteStop("Nugegoda", "නුගේගොඩ", 6.8741, 79.8893, 10.5),
                RouteStop("Maharagama", "මහරගම", 6.8481, 79.9265, 15.0)
            )
        ),
        BusRoute(
            routeNumber = "120",
            englishTitle = "Colombo Fort - Horana",
            sinhalaTitle = "කොළඹ කොටුව - හොරණ",
            startStationEnglish = "Colombo Fort",
            startStationSinhala = "කොළඹ කොටුව",
            endStationEnglish = "Horana",
            endStationSinhala = "හොරණ",
            category = "Semi-Luxury",
            baseFareLkr = 45.0,
            perKmRateLkr = 14.0,
            stops = listOf(
                RouteStop("Colombo Fort", "කොළඹ කොටුව", 6.9348, 79.8436, 0.0),
                RouteStop("Pamankada", "පමන්කඩ", 6.8775, 79.8722, 7.8),
                RouteStop("Kohuwala", "කෝහුවල", 6.8710, 79.8755, 9.2),
                RouteStop("Piliyandala", "පිළියන්දල", 6.7997, 79.9230, 18.5),
                RouteStop("Kahathuduwa", "කහතුඩුව", 6.7725, 79.9710, 27.0),
                RouteStop("Horana", "හොරණ", 6.7126, 80.0621, 38.0)
            )
        ),
        BusRoute(
            routeNumber = "EX-001",
            englishTitle = "Maharagama - Galle (Highway)",
            sinhalaTitle = "මහරගම - ගාල්ල (අධිවේගී)",
            startStationEnglish = "Maharagama",
            startStationSinhala = "මහරගම",
            endStationEnglish = "Galle",
            endStationSinhala = "ගාල්ල",
            category = "AC-Expressway",
            baseFareLkr = 180.0,
            perKmRateLkr = 22.0,
            stops = listOf(
                RouteStop("Maharagama", "මහරගම", 6.8481, 79.9265, 0.0),
                RouteStop("Kottawa Inter.", "කොට්ටාව පිවිසුම", 6.8415, 79.9620, 5.0),
                RouteStop("Gelanigama", "ගෙලනිගම", 6.7265, 80.0245, 28.0),
                RouteStop("Welipenna", "වැලිපැන්න", 6.4385, 80.1260, 65.0),
                RouteStop("Pinnaduwa", "පින්නදූව", 6.0694, 80.2482, 116.0),
                RouteStop("Galle Bus Stand", "ගාල්ල බස් නැවතුම", 6.0331, 80.2152, 121.0)
            )
        ),
        BusRoute(
            routeNumber = "01",
            englishTitle = "Colombo - Kandy",
            sinhalaTitle = "කොළඹ - මහනුවර",
            startStationEnglish = "Colombo Central",
            startStationSinhala = "කොළඹ මධ්‍යම",
            endStationEnglish = "Kandy Goods Shed",
            endStationSinhala = "මහනුවර",
            category = "Normal",
            baseFareLkr = 30.0,
            perKmRateLkr = 9.5,
            stops = listOf(
                RouteStop("Colombo Fort", "කොළඹ කොටුව", 6.9344, 79.8518, 0.0),
                RouteStop("Peliyagoda", "පෑලියගොඩ", 6.9680, 79.8820, 6.0),
                RouteStop("Kadawatha", "කඩවත", 7.0016, 79.9531, 16.0),
                RouteStop("Yakkala", "යක්කල", 7.0911, 80.0315, 31.0),
                RouteStop("Warakapola", "වරකාපොල", 7.2215, 80.1985, 57.0),
                RouteStop("Kegalle", "කෑගල්ල", 7.2513, 80.3458, 78.0),
                RouteStop("Mawanella", "මාවනැල්ල", 7.2526, 80.4485, 92.0),
                RouteStop("Kadugannawa", "කඩුගන්නාව", 7.2541, 80.5212, 102.0),
                RouteStop("Peradeniya", "පේරාදෙනිය", 7.2685, 80.5925, 110.0),
                RouteStop("Kandy", "මහනුවර", 7.2906, 80.6337, 116.0)
            )
        )
    )
}
