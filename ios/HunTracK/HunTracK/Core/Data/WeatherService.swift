import Foundation

public class WeatherService: ObservableObject {
    public static let shared = WeatherService()
    
    @Published public var weather: WeatherSnapshot?
    @Published public var loading: Bool = false
    @Published public var error: String?
    
    private init() {}
    
    public func fetchWeather(latitude: Double, longitude: Double) {
        loading = true
        error = nil
        
        let urlStr = "https://api.open-meteo.com/v1/forecast?latitude=\(latitude)&longitude=\(longitude)&current=temperature_2m,apparent_temperature,relative_humidity_2m,surface_pressure,wind_speed_10m,wind_direction_10m,wind_gusts_10m,weather_code,cloud_cover,visibility&timezone=Europe/London"
        
        guard let url = URL(string: urlStr) else {
            loading = false
            return
        }
        
        URLSession.shared.dataTask(with: url) { [weak self] data, response, err in
            DispatchQueue.main.async {
                self?.loading = false
                if let err = err {
                    self?.error = err.localizedDescription
                    return
                }
                guard let data = data,
                      let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let current = json["current"] as? [String: Any] else {
                    self?.error = "Unable to parse forecast"
                    return
                }
                
                let temp = current["temperature_2m"] as? Double ?? 0.0
                let feels = current["apparent_temperature"] as? Double ?? temp
                let humidity = current["relative_humidity_2m"] as? Int ?? 0
                let pressure = current["surface_pressure"] as? Double ?? 1013.0
                let wind = current["wind_speed_10m"] as? Double ?? 0.0
                let dir = current["wind_direction_10m"] as? Double ?? 0.0
                let gusts = current["wind_gusts_10m"] as? Double
                let code = current["weather_code"] as? Int ?? 0
                let clouds = current["cloud_cover"] as? Int
                let vis = (current["visibility"] as? Double).map { $0 / 1000.0 }
                
                self?.weather = WeatherSnapshot(
                    latitude: latitude,
                    longitude: longitude,
                    fetchedAtEpochMs: Int64(Date().timeIntervalSince1970 * 1000),
                    temperatureC: temp,
                    feelsLikeC: feels,
                    humidityPercent: humidity,
                    pressureHpa: pressure,
                    windSpeedKmh: wind,
                    windDirectionDeg: dir,
                    windGustKmh: gusts,
                    rainProbabilityPercent: nil,
                    visibilityKm: vis,
                    weatherCode: code,
                    cloudCoverPercent: clouds
                )
            }
        }.resume()
    }
}
