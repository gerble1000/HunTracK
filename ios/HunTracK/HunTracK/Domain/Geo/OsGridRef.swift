import Foundation

public struct LatLng: Equatable {
    public let latitude: Double
    public let longitude: Double
    
    public init(latitude: Double, longitude: Double) {
        self.latitude = latitude
        self.longitude = longitude
    }
}

public struct GridLine {
    public let points: [LatLng]
    public let major: Bool
}

public enum OsGridRef {
    private static let aWgs84 = 6378137.0
    private static let fWgs84 = 1.0 / 298.257223563
    private static let aAiry = 6377563.396
    private static let bAiry = 6356256.909
    private static let fAiry = 1.0 / 299.3249646
    private static let e2 = (aAiry * aAiry - bAiry * bAiry) / (aAiry * aAiry)
    private static let f0 = 0.9996012717
    private static let phi0 = 49.0 * .pi / 180.0
    private static let lambda0 = -2.0 * .pi / 180.0
    private static let e0 = 400000.0
    private static let n0 = -100000.0
    private static let arcsecToRad = Double.pi / (180.0 * 3600.0)

    public static func latLngToOsGrid(latitude: Double, longitude: Double, digits: Int = 10) -> String? {
        guard let (easting, northing) = wgs84ToEastNorth(lat: latitude, lon: longitude) else { return nil }
        return eastNorthToGridRef(easting: easting, northing: northing, digits: digits)
    }

    public static func osGridToLatLng(_ ref: String) -> LatLng? {
        guard let (easting, northing) = gridRefToEastNorth(ref) else { return nil }
        return eastNorthToWgs84(easting: easting, northing: northing)
    }

    public static func isValidOsGridRef(_ ref: String) -> Bool {
        return gridRefToEastNorth(ref) != nil
    }

    /// British National Grid lines, not a latitude/longitude graticule. Sampling
    /// both axes keeps the OSGB projection's curvature at wider zoom levels.
    public static func gridLinesForViewport(
        south: Double, west: Double, north: Double, east: Double, spacingMeters: Double
    ) -> [GridLine] {
        guard spacingMeters > 0, south < north, west < east,
              north >= 49, south <= 61, east >= -9, west <= 3 else { return [] }
        let bottom = max(49, south), top = min(61, north)
        let left = max(-9, west), right = min(3, east)
        var projected: [(Double, Double)] = []
        for row in 0...4 {
            for column in 0...4 {
                let lat = bottom + (top - bottom) * Double(row) / 4
                let lon = left + (right - left) * Double(column) / 4
                if let point = wgs84ToEastNorth(lat: lat, lon: lon) { projected.append(point) }
            }
        }
        guard !projected.isEmpty else { return [] }
        let eMin = max(0, projected.map { $0.0 }.min()! - spacingMeters)
        let eMax = min(700000, projected.map { $0.0 }.max()! + spacingMeters)
        let nMin = max(0, projected.map { $0.1 }.min()! - spacingMeters)
        let nMax = min(1300000, projected.map { $0.1 }.max()! + spacingMeters)
        let majorEvery = spacingMeters >= 10000 ? 100000.0 : (spacingMeters >= 1000 ? 10000.0 : 1000.0)
        var lines: [GridLine] = []
        for vertical in [true, false] {
            let lower = vertical ? eMin : nMin
            let upper = vertical ? eMax : nMax
            let start = floor(lower / spacingMeters) * spacingMeters
            let count = min(48, max(0, Int((upper - start) / spacingMeters) + 1))
            for index in 0..<count {
                let value = start + Double(index) * spacingMeters
                let points = (0...32).compactMap { sample -> LatLng? in
                    let fraction = Double(sample) / 32
                    return eastNorthToWgs84(
                        easting: vertical ? value : eMin + (eMax - eMin) * fraction,
                        northing: vertical ? nMin + (nMax - nMin) * fraction : value
                    )
                }
                if points.count >= 2 {
                    lines.append(GridLine(points: points, major: abs(value.truncatingRemainder(dividingBy: majorEvery)) < 0.5))
                }
            }
        }
        return lines
    }

    private static func wgs84ToEastNorth(lat: Double, lon: Double) -> (Double, Double)? {
        let (phi, lambda) = wgs84ToOsgb36(phi: lat * .pi / 180.0, lambda: lon * .pi / 180.0)
        let (e, n) = latLonToEastNorth(phi: phi, lambda: lambda)
        guard e >= 0, e <= 700000, n >= 0, n <= 1300000 else { return nil }
        return (e, n)
    }

    private static func eastNorthToWgs84(easting: Double, northing: Double) -> LatLng? {
        guard easting >= 0, easting <= 700000, northing >= 0, northing <= 1300000 else { return nil }
        let (phi, lambda) = eastNorthToLatLon(easting: easting, northing: northing)
        let (wgsPhi, wgsLambda) = osgb36ToWgs84(phi: phi, lambda: lambda)
        return LatLng(latitude: wgsPhi * 180.0 / .pi, longitude: wgsLambda * 180.0 / .pi)
    }

    private static func wgs84ToOsgb36(phi: Double, lambda: Double) -> (Double, Double) {
        let (x, y, z) = latLonToCartesian(phi: phi, lambda: lambda, a: aWgs84, f: fWgs84)
        let (x2, y2, z2) = helmert(x: x, y: y, z: z, tx: -446.448, ty: 125.157, tz: -542.060, sPpm: 20.4894, rxArcsec: -0.1502, ryArcsec: -0.2470, rzArcsec: -0.8421)
        return cartesianToLatLon(x: x2, y: y2, z: z2, a: aAiry, f: fAiry)
    }

    private static func osgb36ToWgs84(phi: Double, lambda: Double) -> (Double, Double) {
        let (x, y, z) = latLonToCartesian(phi: phi, lambda: lambda, a: aAiry, f: fAiry)
        let (x2, y2, z2) = helmert(x: x, y: y, z: z, tx: 446.448, ty: -125.157, tz: 542.060, sPpm: -20.4894, rxArcsec: 0.1502, ryArcsec: 0.2470, rzArcsec: 0.8421)
        return cartesianToLatLon(x: x2, y: y2, z: z2, a: aWgs84, f: fWgs84)
    }

    private static func helmert(x: Double, y: Double, z: Double, tx: Double, ty: Double, tz: Double, sPpm: Double, rxArcsec: Double, ryArcsec: Double, rzArcsec: Double) -> (Double, Double, Double) {
        let s = sPpm / 1e6 + 1.0
        let rx = rxArcsec * arcsecToRad
        let ry = ryArcsec * arcsecToRad
        let rz = rzArcsec * arcsecToRad
        return (
            tx + s * (x - rz * y + ry * z),
            ty + s * (rz * x + y - rx * z),
            tz + s * (-ry * x + rx * y + z)
        )
    }

    private static func latLonToCartesian(phi: Double, lambda: Double, a: Double, f: Double) -> (Double, Double, Double) {
        let e2 = 2 * f - f * f
        let sinPhi = sin(phi)
        let cosPhi = cos(phi)
        let nu = a / sqrt(1 - e2 * sinPhi * sinPhi)
        return (
            nu * cosPhi * cos(lambda),
            nu * cosPhi * sin(lambda),
            nu * (1 - e2) * sinPhi
        )
    }

    private static func cartesianToLatLon(x: Double, y: Double, z: Double, a: Double, f: Double) -> (Double, Double) {
        let e2 = 2 * f - f * f
        let p = sqrt(x * x + y * y)
        var phi = atan2(z, p * (1 - e2))
        for _ in 0..<10 {
            let sinPhi = sin(phi)
            let nu = a / sqrt(1 - e2 * sinPhi * sinPhi)
            let phiNext = atan2(z + e2 * nu * sinPhi, p)
            if abs(phiNext - phi) < 1e-12 {
                phi = phiNext
                break
            }
            phi = phiNext
        }
        return (phi, atan2(y, x))
    }

    private static func latLonToEastNorth(phi: Double, lambda: Double) -> (Double, Double) {
        let cosPhi = cos(phi)
        let sinPhi = sin(phi)
        let tanPhi = tan(phi)
        let tan2 = tanPhi * tanPhi
        let tan4 = tan2 * tan2
        let nu = aAiry * f0 / sqrt(1 - e2 * sinPhi * sinPhi)
        let rho = aAiry * f0 * (1 - e2) / pow(1 - e2 * sinPhi * sinPhi, 1.5)
        let eta2 = nu / rho - 1

        let m = meridionalArc(phi)
        let dLon = lambda - lambda0
        let dLon2 = dLon * dLon
        let dLon3 = dLon2 * dLon
        let dLon4 = dLon3 * dLon
        let dLon5 = dLon4 * dLon
        let dLon6 = dLon5 * dLon

        let I = m + n0
        let II = (nu / 2) * sinPhi * cosPhi
        let III = (nu / 24) * sinPhi * pow(cosPhi, 3) * (5 - tan2 + 9 * eta2)
        let IIIA = (nu / 720) * sinPhi * pow(cosPhi, 5) * (61 - 58 * tan2 + tan4)
        let IV = nu * cosPhi
        let V = (nu / 6) * pow(cosPhi, 3) * (nu / rho - tan2)
        let VI = (nu / 120) * pow(cosPhi, 5) * (5 - 18 * tan2 + tan4 + 14 * eta2 - 58 * tan2 * eta2)

        let easting = e0 + IV * dLon + V * dLon3 + VI * dLon5
        let northing = I + II * dLon2 + III * dLon4 + IIIA * dLon6
        return (easting, northing)
    }

    private static func eastNorthToLatLon(easting: Double, northing: Double) -> (Double, Double) {
        var phi = phi0
        var m: Double = 0
        let nPrim = northing - n0
        for _ in 0..<20 {
            m = meridionalArc(phi)
            let delta = nPrim - m
            if abs(delta) < 1e-5 { break }
            phi += delta / (aAiry * f0)
        }

        let cosPhi = cos(phi)
        let sinPhi = sin(phi)
        let tanPhi = tan(phi)
        let tan2 = tanPhi * tanPhi
        let tan4 = tan2 * tan2
        let tan6 = tan4 * tan2
        let secPhi = 1.0 / cosPhi
        let nu = aAiry * f0 / sqrt(1 - e2 * sinPhi * sinPhi)
        let rho = aAiry * f0 * (1 - e2) / pow(1 - e2 * sinPhi * sinPhi, 1.5)
        let eta2 = nu / rho - 1

        let dE = easting - e0
        let dE2 = dE * dE
        let dE3 = dE2 * dE
        let dE4 = dE3 * dE
        let dE5 = dE4 * dE
        let dE6 = dE5 * dE
        let dE7 = dE6 * dE

        let VII = tanPhi / (2 * rho * nu)
        let VIII = tanPhi / (24 * rho * pow(nu, 3)) * (5 + 3 * tan2 + eta2 - 9 * tan2 * eta2)
        let IX = tanPhi / (720 * rho * pow(nu, 5)) * (61 + 90 * tan2 + 45 * tan4)
        let X = secPhi / nu
        let XI = secPhi / (6 * pow(nu, 3)) * (nu / rho + 2 * tan2)
        let XII = secPhi / (120 * pow(nu, 5)) * (5 + 28 * tan2 + 24 * tan4)
        let XIIA = secPhi / (5040 * pow(nu, 7)) * (61 + 662 * tan2 + 1320 * tan4 + 720 * tan6)

        let lat = phi - VII * dE2 + VIII * dE4 - IX * dE6
        let lon = lambda0 + X * dE - XI * dE3 + XII * dE5 - XIIA * dE7
        return (lat, lon)
    }

    private static func meridionalArc(_ phi: Double) -> Double {
        let n = (aAiry - bAiry) / (aAiry + bAiry)
        let n2 = n * n
        let n3 = n2 * n
        let phiDiff = phi - phi0
        let phiSum = phi + phi0
        return bAiry * f0 * (
            (1 + n + 5.0 / 4 * n2 + 5.0 / 4 * n3) * phiDiff -
            (3 * n + 3 * n2 + 21.0 / 8 * n3) * sin(phiDiff) * cos(phiSum) +
            (15.0 / 8 * n2 + 15.0 / 8 * n3) * sin(2 * phiDiff) * cos(2 * phiSum) -
            (35.0 / 24 * n3) * sin(3 * phiDiff) * cos(3 * phiSum)
        )
    }

    public static func eastNorthToGridRef(easting: Double, northing: Double, digits: Int = 10) -> String? {
        guard easting >= 0, easting <= 700000, northing >= 0, northing <= 1300000 else { return nil }
        let e100k = Int(floor(easting / 100000))
        let n100k = Int(floor(northing / 100000))
        guard e100k >= 0, e100k <= 6, n100k >= 0, n100k <= 12 else { return nil }

        let l1 = (19 - n100k) - (19 - n100k) % 5 + ((e100k + 10) / 5)
        let l2 = ((19 - n100k) * 5) % 25 + e100k % 5
        
        func letter(index: Int) -> Character {
            var i = index
            if i > 7 { i += 1 }
            return Character(UnicodeScalar(65 + i)!)
        }
        let letters = "\(letter(index: l1))\(letter(index: l2))"

        let e = Int(floor(easting.truncatingRemainder(dividingBy: 100000)))
        let n = Int(floor(northing.truncatingRemainder(dividingBy: 100000)))
        let pairs = digits / 2
        let factor = Int(pow(10.0, Double(5 - pairs)))
        let eStr = String(format: "%0*d", pairs, e / factor)
        let nStr = String(format: "%0*d", pairs, n / factor)
        return "\(letters) \(eStr) \(nStr)"
    }

    public static func gridRefToEastNorth(_ ref: String) -> (Double, Double)? {
        let compact = ref.trimmingCharacters(in: .whitespacesAndNewlines)
            .uppercased()
            .replacingOccurrences(of: ",", with: " ")
            .replacingOccurrences(of: " ", with: "")
        
        guard let regex = try? NSRegularExpression(pattern: "^([A-HJ-Z]{2})(\\d{6}|\\d{8}|\\d{10})$") else { return nil }
        let range = NSRange(location: 0, length: compact.utf16.count)
        guard let match = regex.firstMatch(in: compact, options: [], range: range) else { return nil }
        
        let letters = (compact as NSString).substring(with: match.range(at: 1))
        let digits = (compact as NSString).substring(with: match.range(at: 2))
        let pairs = digits.count / 2
        let ePart = String(digits.prefix(pairs))
        let nPart = String(digits.suffix(pairs))

        func letterIndex(_ c: Character) -> Int? {
            guard let ascii = c.asciiValue, ascii >= 65, ascii <= 90, c != "I" else { return nil }
            var i = Int(ascii - 65)
            if i > 7 { i -= 1 }
            return (i >= 0 && i <= 24) ? i : nil
        }
        
        guard let l1 = letterIndex(letters.first!), let l2 = letterIndex(letters.last!) else { return nil }

        let e100km = floorMod(l1 - 2, 5) * 5 + (l2 % 5)
        let n100km = (19 - (l1 / 5) * 5) - (l2 / 5)

        let resolution = pow(10.0, Double(5 - pairs))
        guard let eVal = Double(ePart), let nVal = Double(nPart) else { return nil }
        let easting = Double(e100km) * 100000.0 + eVal * resolution + resolution / 2.0
        let northing = Double(n100km) * 100000.0 + nVal * resolution + resolution / 2.0
        guard easting >= 0, easting <= 700000, northing >= 0, northing <= 1300000 else { return nil }
        return (easting, northing)
    }

    private static func floorMod(_ a: Int, _ m: Int) -> Int {
        let r = a % m
        return r < 0 ? r + m : r
    }
}
