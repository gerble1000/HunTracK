import SwiftUI
import UIKit
import Foundation

extension Notification.Name {
    static let boundaryCaptureFinished = Notification.Name("hunttrack.boundaryCaptureFinished")
    static let openMapTab = Notification.Name("hunttrack.openMapTab")
}

/// Readable minimum text sizes which also follow the user's Dynamic Type setting.
extension Font {
    static func field(size: CGFloat, weight: Font.Weight = .regular, design: Font.Design = .default) -> Font {
        let style: Font.TextStyle
        switch size {
        case ..<14: style = .subheadline
        case ..<18: style = .body
        case ..<22: style = .title3
        case ..<26: style = .title2
        case ..<31: style = .title
        default: style = .largeTitle
        }
        return .system(style, design: design).weight(weight)
    }
}

/// Shared silhouette / category artwork for the marker picker, logs and map pins.
/// Animals are drawn as silhouettes (the same shapes Android uses), never photos.
public enum MarkerArtwork {
    public static let markerDiameter: CGFloat = 72
    public static let cullDiameter: CGFloat = 88

    private static var cache: [String: UIImage] = [:]

    /// Same species-to-category mapping as Android `MarkerIconResolver.categoryForSpecies`.
    public static func category(for species: Species) -> MarkerCategory {
        switch species.id {
        case "fox": return .fox
        case "rabbit", "brown_hare": return .rabbit
        default:
            switch species.group {
            case .deer: return .deer
            case .bird, .waterfowl, .corvid: return .bird
            case .pest: return .otherWildlife
            case .otherQuarry: return .fox
            }
        }
    }

    public static func icon(category: MarkerCategory, speciesId: String? = nil, diameter: CGFloat) -> UIImage {
        let glyph = resolveGlyph(category: category, speciesId: speciesId)
        let key = "\(glyph.cacheKey)-\(Int(diameter.rounded()))"
        if let cached = cache[key] { return cached }
        let image = render(glyph: glyph, diameter: max(16, diameter))
        cache[key] = image
        return image
    }

    private enum Glyph {
        case deer, fox, rabbit, bird, badger, other, hare, duck, goose, crow, rat, pest, snipe, woodcock
        case highSeat, hide, skull, feed, camera, caller, water, salt, treeStand
        case parking, gate, fence, boundary, danger, shelter, campsite, flag, footprint, blood, hair
        case droppings, bedding, sighting

        var cacheKey: String { String(describing: self) }

        var glyphColor: UIColor {
            switch self {
            case .water: return UIColor(red: 0.12, green: 0.53, blue: 0.90, alpha: 1)
            case .blood: return UIColor(red: 0.86, green: 0.12, blue: 0.16, alpha: 1)
            case .skull: return UIColor(red: 0.96, green: 0.94, blue: 0.90, alpha: 1)
            case .fox: return UIColor(red: 0.72, green: 0.32, blue: 0.12, alpha: 1)
            case .deer, .hare: return UIColor(red: 0.29, green: 0.16, blue: 0.08, alpha: 1)
            case .duck, .goose: return UIColor(red: 0.16, green: 0.32, blue: 0.28, alpha: 1)
            case .crow, .badger: return UIColor(red: 0.12, green: 0.10, blue: 0.08, alpha: 1)
            case .danger: return UIColor(red: 0.85, green: 0.25, blue: 0.18, alpha: 1)
            default: return UIColor(red: 0.16, green: 0.10, blue: 0.05, alpha: 1)
            }
        }

        var badgeFill: UIColor {
            switch self {
            case .skull: return UIColor(red: 0.45, green: 0.08, blue: 0.08, alpha: 1)
            case .water: return UIColor(red: 0.10, green: 0.28, blue: 0.48, alpha: 1)
            case .blood: return UIColor(red: 0.42, green: 0.08, blue: 0.10, alpha: 1)
            default: return UIColor(red: 0.89, green: 0.69, blue: 0.36, alpha: 1)
            }
        }
    }

    private static func resolveGlyph(category: MarkerCategory, speciesId: String?) -> Glyph {
        if category == .cull { return .skull }
        if let speciesId = speciesId, let species = UkFieldCatalog.speciesById(speciesId) {
            return glyph(for: species)
        }
        switch category {
        case .deer: return .deer
        case .fox: return .fox
        case .rabbit: return .rabbit
        case .bird: return .bird
        case .badger: return .badger
        case .otherWildlife: return .other
        case .highSeat: return .highSeat
        case .hide: return .hide
        case .cull: return .skull
        case .feedingArea: return .feed
        case .trailCamera: return .camera
        case .caller: return .caller
        case .waterSource: return .water
        case .saltLick: return .salt
        case .treeStand: return .treeStand
        case .parking: return .parking
        case .gate: return .gate
        case .fence: return .fence
        case .boundary: return .boundary
        case .dangerArea: return .danger
        case .shelter: return .shelter
        case .campsite: return .campsite
        case .homing: return .flag
        case .footprint: return .footprint
        case .blood: return .blood
        case .hair: return .hair
        case .droppings: return .droppings
        case .beddingArea: return .bedding
        case .lastSighting: return .sighting
        }
    }

    /// Same species → silhouette mapping as Android `MarkerIconResolver`.
    private static func glyph(for species: Species) -> Glyph {
        switch species.id {
        case "fox": return .fox
        case "rabbit": return .rabbit
        case "brown_hare": return .hare
        case "american_mink", "grey_squirrel": return .pest
        case "brown_rat", "black_rat": return .rat
        case "carrion_crow", "hooded_crow", "rook", "jackdaw", "magpie", "jay": return .crow
        case "common_snipe": return .snipe
        case "woodcock": return .woodcock
        case "canada_goose", "greylag_goose", "pink_footed_goose", "white_fronted_goose": return .goose
        case "mallard", "teal", "wigeon", "pintail", "shoveler", "gadwall",
             "goldeneye", "tufted_duck", "pochard", "scaup": return .duck
        default:
            switch species.group {
            case .deer: return .deer
            case .otherQuarry: return .fox
            case .bird: return .bird
            case .waterfowl: return .duck
            case .corvid: return .crow
            case .pest: return .pest
            }
        }
    }

    private static func render(glyph: Glyph, diameter: CGFloat) -> UIImage {
        let size = CGSize(width: diameter, height: diameter)
        let image = UIGraphicsImageRenderer(size: size).image { _ in
            let rect = CGRect(origin: .zero, size: size)
            let shadow = UIBezierPath(ovalIn: rect.insetBy(dx: diameter * 0.02, dy: diameter * 0.02))
            UIColor(white: 0, alpha: 0.45).setFill()
            shadow.fill()
            let badge = UIBezierPath(ovalIn: rect.insetBy(dx: diameter * 0.06, dy: diameter * 0.06))
            glyph.badgeFill.setFill()
            badge.fill()
            UIColor(white: 0.08, alpha: 0.85).setStroke()
            badge.lineWidth = max(1.5, diameter * 0.035)
            badge.stroke()
            let content = rect.insetBy(dx: diameter * 0.2, dy: diameter * 0.2)
            draw(glyph, in: content)
        }
        return image.withRenderingMode(.alwaysOriginal)
    }

    private static func draw(_ glyph: Glyph, in rect: CGRect) {
        let color = glyph.glyphColor
        switch glyph {
        case .deer: drawDeer(in: rect, color: color)
        case .fox: drawFox(in: rect, color: color)
        case .rabbit, .hare: drawRabbit(in: rect, color: color, longEars: glyph == .hare || glyph == .rabbit)
        case .bird, .woodcock, .snipe: drawBird(in: rect, color: color, longBill: glyph != .bird)
        case .badger: drawBadger(in: rect)
        case .duck, .goose: drawDuck(in: rect, color: color, goose: glyph == .goose)
        case .crow: drawBird(in: rect, color: color, longBill: false)
        case .rat, .pest, .other: drawOther(in: rect, color: color)
        case .highSeat, .treeStand: drawHighSeat(in: rect, color: color)
        case .hide, .shelter, .campsite: drawTent(in: rect, color: color)
        case .skull: drawSkull(in: rect, color: color)
        case .feed, .salt: drawLeaf(in: rect, color: glyph == .salt ? UIColor(red: 0.75, green: 0.62, blue: 0.2, alpha: 1) : color)
        case .camera: drawCamera(in: rect, color: color)
        case .caller: drawCaller(in: rect, color: color)
        case .water: drawDrop(in: rect, color: color)
        case .parking: drawParking(in: rect, color: color)
        case .gate: drawGate(in: rect, color: color)
        case .fence: drawFence(in: rect, color: color)
        case .boundary: drawBoundary(in: rect, color: color)
        case .danger: drawDanger(in: rect, color: color)
        case .flag: drawFlag(in: rect, color: color)
        case .footprint: drawFootprint(in: rect, color: color)
        case .blood: drawDrop(in: rect, color: color)
        case .hair: drawHair(in: rect, color: color)
        case .droppings: drawDroppings(in: rect, color: color)
        case .bedding: drawBedding(in: rect, color: color)
        case .sighting: drawEye(in: rect, color: color)
        }
    }

    private static func p(_ x: CGFloat, _ y: CGFloat, _ r: CGRect) -> CGPoint {
        CGPoint(x: r.minX + r.width * x, y: r.minY + r.height * y)
    }

    private static func fill(_ path: UIBezierPath, _ color: UIColor) {
        color.setFill()
        path.fill()
    }

    private static func drawDeer(in r: CGRect, color: UIColor) {
        let antler = UIBezierPath()
        antler.move(to: p(0.32, 0.42, r)); antler.addLine(to: p(0.18, 0.08, r)); antler.addLine(to: p(0.28, 0.28, r))
        antler.addLine(to: p(0.10, 0.18, r)); antler.addLine(to: p(0.26, 0.36, r)); antler.close()
        antler.move(to: p(0.68, 0.42, r)); antler.addLine(to: p(0.82, 0.08, r)); antler.addLine(to: p(0.72, 0.28, r))
        antler.addLine(to: p(0.90, 0.18, r)); antler.addLine(to: p(0.74, 0.36, r)); antler.close()
        fill(antler, color)
        fill(UIBezierPath(ovalIn: CGRect(x: p(0.28, 0.34, r).x, y: p(0.28, 0.34, r).y, width: r.width * 0.44, height: r.height * 0.34)), color)
        let neck = UIBezierPath()
        neck.move(to: p(0.38, 0.62, r)); neck.addLine(to: p(0.34, 0.98, r)); neck.addLine(to: p(0.66, 0.98, r)); neck.addLine(to: p(0.62, 0.62, r)); neck.close()
        fill(neck, color)
    }

    private static func drawFox(in r: CGRect, color: UIColor) {
        let path = UIBezierPath()
        path.move(to: p(0.30, 0.38, r)); path.addLine(to: p(0.16, 0.08, r)); path.addLine(to: p(0.40, 0.30, r))
        path.move(to: p(0.70, 0.38, r)); path.addLine(to: p(0.84, 0.08, r)); path.addLine(to: p(0.60, 0.30, r))
        path.close()
        fill(path, color)
        fill(UIBezierPath(ovalIn: CGRect(x: p(0.24, 0.30, r).x, y: p(0.24, 0.30, r).y, width: r.width * 0.52, height: r.height * 0.42)), color)
        let snout = UIBezierPath()
        snout.move(to: p(0.38, 0.58, r)); snout.addLine(to: p(0.50, 0.78, r)); snout.addLine(to: p(0.62, 0.58, r)); snout.close()
        fill(snout, color)
        let body = UIBezierPath()
        body.move(to: p(0.36, 0.68, r)); body.addLine(to: p(0.30, 0.98, r)); body.addLine(to: p(0.70, 0.98, r)); body.addLine(to: p(0.64, 0.68, r)); body.close()
        fill(body, color)
    }

    private static func drawRabbit(in r: CGRect, color: UIColor, longEars: Bool) {
        let earW = r.width * 0.16
        let earH = r.height * (longEars ? 0.52 : 0.42)
        fill(UIBezierPath(roundedRect: CGRect(x: p(0.28, 0.02, r).x, y: p(0.28, 0.02, r).y, width: earW, height: earH), cornerRadius: earW / 2), color)
        fill(UIBezierPath(roundedRect: CGRect(x: p(0.56, 0.02, r).x, y: p(0.56, 0.02, r).y, width: earW, height: earH), cornerRadius: earW / 2), color)
        fill(UIBezierPath(ovalIn: CGRect(x: p(0.22, 0.40, r).x, y: p(0.22, 0.40, r).y, width: r.width * 0.56, height: r.height * 0.48)), color)
    }

    private static func drawBird(in r: CGRect, color: UIColor, longBill: Bool) {
        fill(UIBezierPath(ovalIn: CGRect(x: p(0.18, 0.32, r).x, y: p(0.18, 0.32, r).y, width: r.width * 0.48, height: r.height * 0.42)), color)
        fill(UIBezierPath(ovalIn: CGRect(x: p(0.48, 0.18, r).x, y: p(0.48, 0.18, r).y, width: r.width * 0.28, height: r.height * 0.26)), color)
        let bill = UIBezierPath()
        bill.move(to: p(0.72, 0.26, r))
        bill.addLine(to: p(longBill ? 0.98 : 0.90, 0.32, r))
        bill.addLine(to: p(0.72, 0.38, r))
        bill.close()
        fill(bill, color)
        let wing = UIBezierPath()
        wing.move(to: p(0.30, 0.48, r)); wing.addLine(to: p(0.08, 0.72, r)); wing.addLine(to: p(0.42, 0.70, r)); wing.close()
        fill(wing, color)
    }

    private static func drawBadger(in r: CGRect) {
        let dark = UIColor(red: 0.12, green: 0.10, blue: 0.08, alpha: 1)
        let light = UIColor(red: 0.93, green: 0.90, blue: 0.82, alpha: 1)
        fill(UIBezierPath(ovalIn: CGRect(x: p(0.18, 0.22, r).x, y: p(0.18, 0.22, r).y, width: r.width * 0.64, height: r.height * 0.62)), dark)
        let stripe = UIBezierPath()
        stripe.move(to: p(0.42, 0.22, r)); stripe.addLine(to: p(0.58, 0.22, r)); stripe.addLine(to: p(0.54, 0.84, r)); stripe.addLine(to: p(0.46, 0.84, r)); stripe.close()
        fill(stripe, light)
    }

    private static func drawDuck(in r: CGRect, color: UIColor, goose: Bool) {
        fill(UIBezierPath(ovalIn: CGRect(x: p(0.08, 0.40, r).x, y: p(0.08, 0.40, r).y, width: r.width * 0.62, height: r.height * 0.40)), color)
        fill(UIBezierPath(ovalIn: CGRect(x: p(0.52, 0.16, r).x, y: p(0.52, 0.16, r).y, width: r.width * (goose ? 0.24 : 0.28), height: r.height * 0.30)), color)
        let neck = UIBezierPath()
        if goose {
            neck.move(to: p(0.58, 0.42, r)); neck.addLine(to: p(0.62, 0.16, r)); neck.addLine(to: p(0.74, 0.16, r)); neck.addLine(to: p(0.70, 0.46, r)); neck.close()
            fill(neck, color)
        }
        let bill = UIBezierPath()
        bill.move(to: p(0.76, 0.26, r)); bill.addLine(to: p(0.98, 0.32, r)); bill.addLine(to: p(0.76, 0.40, r)); bill.close()
        fill(bill, UIColor(red: 0.85, green: 0.55, blue: 0.12, alpha: 1))
    }

    private static func drawOther(in r: CGRect, color: UIColor) {
        fill(UIBezierPath(ovalIn: CGRect(x: p(0.22, 0.28, r).x, y: p(0.22, 0.28, r).y, width: r.width * 0.56, height: r.height * 0.46)), color)
        let ears = UIBezierPath()
        ears.move(to: p(0.30, 0.36, r)); ears.addLine(to: p(0.22, 0.10, r)); ears.addLine(to: p(0.40, 0.30, r)); ears.close()
        ears.move(to: p(0.70, 0.36, r)); ears.addLine(to: p(0.78, 0.10, r)); ears.addLine(to: p(0.60, 0.30, r)); ears.close()
        fill(ears, color)
    }

    private static func drawHighSeat(in r: CGRect, color: UIColor) {
        let path = UIBezierPath()
        path.move(to: p(0.50, 0.02, r)); path.addLine(to: p(0.18, 0.55, r)); path.addLine(to: p(0.34, 0.55, r))
        path.addLine(to: p(0.34, 0.98, r)); path.addLine(to: p(0.46, 0.98, r)); path.addLine(to: p(0.46, 0.55, r))
        path.addLine(to: p(0.54, 0.55, r)); path.addLine(to: p(0.54, 0.98, r)); path.addLine(to: p(0.66, 0.98, r))
        path.addLine(to: p(0.66, 0.55, r)); path.addLine(to: p(0.82, 0.55, r)); path.close()
        fill(path, color)
        let seat = UIBezierPath(rect: CGRect(x: p(0.22, 0.62, r).x, y: p(0.22, 0.62, r).y, width: r.width * 0.56, height: r.height * 0.08))
        fill(seat, color)
    }

    private static func drawTent(in r: CGRect, color: UIColor) {
        let tent = UIBezierPath()
        tent.move(to: p(0.50, 0.08, r)); tent.addLine(to: p(0.08, 0.88, r)); tent.addLine(to: p(0.92, 0.88, r)); tent.close()
        fill(tent, color)
        let door = UIBezierPath()
        door.move(to: p(0.50, 0.42, r)); door.addLine(to: p(0.38, 0.88, r)); door.addLine(to: p(0.62, 0.88, r)); door.close()
        fill(door, UIColor(red: 0.89, green: 0.69, blue: 0.36, alpha: 1))
    }

    private static func drawSkull(in r: CGRect, color: UIColor) {
        let bone = UIBezierPath()
        bone.move(to: p(0.06, 0.92, r)); bone.addLine(to: p(0.94, 0.58, r))
        bone.move(to: p(0.06, 0.58, r)); bone.addLine(to: p(0.94, 0.92, r))
        bone.lineWidth = max(3, r.width * 0.14)
        bone.lineCapStyle = .round
        color.setStroke()
        bone.stroke()
        fill(UIBezierPath(ovalIn: CGRect(x: p(0.22, 0.02, r).x, y: p(0.22, 0.02, r).y, width: r.width * 0.56, height: r.height * 0.5)), color)
        let socket = UIColor(red: 0.35, green: 0.05, blue: 0.05, alpha: 1)
        fill(UIBezierPath(ovalIn: CGRect(x: p(0.32, 0.16, r).x, y: p(0.32, 0.16, r).y, width: r.width * 0.14, height: r.height * 0.16)), socket)
        fill(UIBezierPath(ovalIn: CGRect(x: p(0.54, 0.16, r).x, y: p(0.54, 0.16, r).y, width: r.width * 0.14, height: r.height * 0.16)), socket)
        let nose = UIBezierPath()
        nose.move(to: p(0.50, 0.32, r)); nose.addLine(to: p(0.42, 0.44, r)); nose.addLine(to: p(0.58, 0.44, r)); nose.close()
        fill(nose, socket)
        // Redraw the crossbones in front of the jaw so the icon reads as skull and crossbones.
        bone.stroke()
    }

    private static func drawLeaf(in r: CGRect, color: UIColor) {
        let leaf = UIBezierPath()
        leaf.move(to: p(0.50, 0.05, r))
        leaf.addCurve(to: p(0.50, 0.95, r), controlPoint1: p(0.05, 0.25, r), controlPoint2: p(0.05, 0.75, r))
        leaf.addCurve(to: p(0.50, 0.05, r), controlPoint1: p(0.95, 0.75, r), controlPoint2: p(0.95, 0.25, r))
        fill(leaf, color)
    }

    private static func drawCamera(in r: CGRect, color: UIColor) {
        fill(UIBezierPath(roundedRect: CGRect(x: p(0.08, 0.28, r).x, y: p(0.08, 0.28, r).y, width: r.width * 0.84, height: r.height * 0.58), cornerRadius: r.width * 0.08), color)
        fill(UIBezierPath(ovalIn: CGRect(x: p(0.32, 0.38, r).x, y: p(0.32, 0.38, r).y, width: r.width * 0.36, height: r.height * 0.36)), UIColor(red: 0.89, green: 0.69, blue: 0.36, alpha: 1))
        fill(UIBezierPath(rect: CGRect(x: p(0.28, 0.16, r).x, y: p(0.28, 0.16, r).y, width: r.width * 0.28, height: r.height * 0.14)), color)
    }

    private static func drawCaller(in r: CGRect, color: UIColor) {
        fill(UIBezierPath(roundedRect: CGRect(x: p(0.12, 0.32, r).x, y: p(0.12, 0.32, r).y, width: r.width * 0.28, height: r.height * 0.36), cornerRadius: 2), color)
        let waves = UIBezierPath()
        waves.move(to: p(0.50, 0.30, r)); waves.addQuadCurve(to: p(0.50, 0.70, r), controlPoint: p(0.72, 0.50, r))
        waves.move(to: p(0.58, 0.16, r)); waves.addQuadCurve(to: p(0.58, 0.84, r), controlPoint: p(0.92, 0.50, r))
        waves.lineWidth = max(2, r.width * 0.08)
        waves.lineCapStyle = .round
        color.setStroke()
        waves.stroke()
    }

    private static func drawDrop(in r: CGRect, color: UIColor) {
        let drop = UIBezierPath()
        drop.move(to: p(0.50, 0.02, r))
        drop.addCurve(to: p(0.50, 0.96, r), controlPoint1: p(0.08, 0.42, r), controlPoint2: p(0.12, 0.96, r))
        drop.addCurve(to: p(0.50, 0.02, r), controlPoint1: p(0.88, 0.96, r), controlPoint2: p(0.92, 0.42, r))
        fill(drop, color)
    }

    private static func drawParking(in r: CGRect, color: UIColor) {
        let text = "P" as NSString
        let font = UIFont.systemFont(ofSize: r.height * 0.85, weight: .black)
        let attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color]
        let textSize = text.size(withAttributes: attributes)
        text.draw(at: CGPoint(x: r.midX - textSize.width / 2, y: r.midY - textSize.height / 2), withAttributes: attributes)
    }

    private static func drawGate(in r: CGRect, color: UIColor) {
        let posts = UIBezierPath()
        posts.move(to: p(0.18, 0.12, r)); posts.addLine(to: p(0.18, 0.92, r))
        posts.move(to: p(0.82, 0.12, r)); posts.addLine(to: p(0.82, 0.92, r))
        posts.move(to: p(0.18, 0.28, r)); posts.addLine(to: p(0.82, 0.28, r))
        posts.move(to: p(0.18, 0.55, r)); posts.addLine(to: p(0.82, 0.55, r))
        posts.move(to: p(0.18, 0.78, r)); posts.addLine(to: p(0.82, 0.78, r))
        posts.lineWidth = max(2, r.width * 0.08)
        posts.lineCapStyle = .round
        color.setStroke()
        posts.stroke()
    }

    private static func drawFence(in r: CGRect, color: UIColor) {
        let path = UIBezierPath()
        path.move(to: p(0.08, 0.35, r)); path.addLine(to: p(0.92, 0.35, r))
        path.move(to: p(0.08, 0.68, r)); path.addLine(to: p(0.92, 0.68, r))
        for x in [0.18, 0.50, 0.82] as [CGFloat] {
            path.move(to: p(x, 0.12, r)); path.addLine(to: p(x, 0.92, r))
        }
        path.lineWidth = max(2, r.width * 0.07)
        path.lineCapStyle = .round
        color.setStroke()
        path.stroke()
    }

    private static func drawBoundary(in r: CGRect, color: UIColor) {
        let box = UIBezierPath(roundedRect: CGRect(x: p(0.12, 0.12, r).x, y: p(0.12, 0.12, r).y, width: r.width * 0.76, height: r.height * 0.76), cornerRadius: 3)
        box.lineWidth = max(2, r.width * 0.08)
        let dash: [CGFloat] = [r.width * 0.12, r.width * 0.08]
        dash.withUnsafeBufferPointer { buffer in
            box.setLineDash(buffer.baseAddress, count: 2, phase: 0)
        }
        color.setStroke()
        box.stroke()
    }

    private static func drawDanger(in r: CGRect, color: UIColor) {
        let tri = UIBezierPath()
        tri.move(to: p(0.50, 0.04, r)); tri.addLine(to: p(0.06, 0.92, r)); tri.addLine(to: p(0.94, 0.92, r)); tri.close()
        fill(tri, color)
        let bang = "!" as NSString
        let font = UIFont.systemFont(ofSize: r.height * 0.45, weight: .black)
        let attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: UIColor(red: 0.89, green: 0.69, blue: 0.36, alpha: 1)]
        let textSize = bang.size(withAttributes: attributes)
        bang.draw(at: CGPoint(x: r.midX - textSize.width / 2, y: r.midY - textSize.height / 2 + r.height * 0.06), withAttributes: attributes)
    }

    private static func drawFlag(in r: CGRect, color: UIColor) {
        let pole = UIBezierPath()
        pole.move(to: p(0.28, 0.06, r)); pole.addLine(to: p(0.28, 0.96, r))
        pole.lineWidth = max(2, r.width * 0.08)
        pole.lineCapStyle = .round
        color.setStroke()
        pole.stroke()
        let flag = UIBezierPath()
        flag.move(to: p(0.32, 0.10, r)); flag.addLine(to: p(0.92, 0.24, r)); flag.addLine(to: p(0.32, 0.46, r)); flag.close()
        fill(flag, color)
    }

    private static func drawFootprint(in r: CGRect, color: UIColor) {
        fill(UIBezierPath(ovalIn: CGRect(x: p(0.32, 0.38, r).x, y: p(0.32, 0.38, r).y, width: r.width * 0.36, height: r.height * 0.52)), color)
        for origin in [p(0.22, 0.16, r), p(0.42, 0.06, r), p(0.62, 0.16, r)] {
            fill(UIBezierPath(ovalIn: CGRect(x: origin.x, y: origin.y, width: r.width * 0.16, height: r.height * 0.18)), color)
        }
    }

    private static func drawHair(in r: CGRect, color: UIColor) {
        let path = UIBezierPath()
        path.move(to: p(0.15, 0.30, r)); path.addCurve(to: p(0.85, 0.22, r), controlPoint1: p(0.35, 0.05, r), controlPoint2: p(0.6, 0.5, r))
        path.move(to: p(0.12, 0.55, r)); path.addCurve(to: p(0.88, 0.48, r), controlPoint1: p(0.4, 0.3, r), controlPoint2: p(0.6, 0.75, r))
        path.move(to: p(0.18, 0.80, r)); path.addCurve(to: p(0.82, 0.72, r), controlPoint1: p(0.4, 0.55, r), controlPoint2: p(0.62, 0.95, r))
        path.lineWidth = max(2, r.width * 0.07)
        path.lineCapStyle = .round
        color.setStroke()
        path.stroke()
    }

    private static func drawDroppings(in r: CGRect, color: UIColor) {
        for origin in [p(0.18, 0.22, r), p(0.52, 0.14, r), p(0.34, 0.52, r)] {
            fill(UIBezierPath(ovalIn: CGRect(x: origin.x, y: origin.y, width: r.width * 0.30, height: r.height * 0.28)), color)
        }
    }

    private static func drawBedding(in r: CGRect, color: UIColor) {
        fill(UIBezierPath(roundedRect: CGRect(x: p(0.08, 0.42, r).x, y: p(0.08, 0.42, r).y, width: r.width * 0.84, height: r.height * 0.36), cornerRadius: r.width * 0.08), color)
        fill(UIBezierPath(roundedRect: CGRect(x: p(0.12, 0.18, r).x, y: p(0.12, 0.18, r).y, width: r.width * 0.28, height: r.height * 0.28), cornerRadius: 3), color)
    }

    private static func drawEye(in r: CGRect, color: UIColor) {
        let eye = UIBezierPath()
        eye.move(to: p(0.02, 0.50, r))
        eye.addQuadCurve(to: p(0.98, 0.50, r), controlPoint: p(0.50, 0.05, r))
        eye.addQuadCurve(to: p(0.02, 0.50, r), controlPoint: p(0.50, 0.95, r))
        color.setStroke()
        eye.lineWidth = max(2, r.width * 0.08)
        eye.stroke()
        fill(UIBezierPath(ovalIn: CGRect(x: p(0.38, 0.36, r).x, y: p(0.38, 0.36, r).y, width: r.width * 0.24, height: r.height * 0.28)), color)
    }
}

public struct AnimalIcon: View {
    var speciesId: String?
    var category: MarkerCategory = .otherWildlife
    var size: CGFloat = 36

    public var body: some View {
        Image(uiImage: MarkerArtwork.icon(category: category, speciesId: speciesId, diameter: size))
            .resizable()
            .interpolation(.high)
            .scaledToFit()
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}

/// Category row used by Add Field Marker. Icons match the map pins.
public struct MarkerCategoryMenu: View {
    @Binding var category: MarkerCategory
    @ObservedObject private var theme = ThemeManager.shared

    public init(category: Binding<MarkerCategory>) {
        _category = category
    }

    public var body: some View {
        Menu {
            ForEach(MarkerCategory.allCases, id: \.self) { cat in
                Button {
                    category = cat
                } label: {
                    Label {
                        Text(category == cat ? "\(cat.label)  ✓" : cat.label)
                    } icon: {
                        Image(uiImage: MarkerArtwork.icon(category: cat, diameter: 28))
                    }
                }
            }
        } label: {
            HStack(spacing: 10) {
                Image(uiImage: MarkerArtwork.icon(category: category, diameter: 36))
                    .resizable()
                    .frame(width: 36, height: 36)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Category")
                        .font(.field(size: 12))
                        .foregroundColor(theme.palette.mist)
                    Text(category.label)
                        .font(.field(size: 16, weight: .semibold))
                        .foregroundColor(theme.palette.cream)
                }
                Spacer()
                Image(systemName: "chevron.up.chevron.down")
                    .font(.field(size: 12, weight: .bold))
                    .foregroundColor(theme.palette.bronzeBright)
            }
            .contentShape(Rectangle())
        }
    }
}

public struct GlassPanel<Content: View>: View {
    let compact: Bool
    let content: Content
    @ObservedObject private var theme = ThemeManager.shared
    
    public init(compact: Bool = false, @ViewBuilder content: () -> Content) {
        self.compact = compact
        self.content = content()
    }
    
    public var body: some View {
        VStack(alignment: .leading, spacing: compact ? 6 : 10) {
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(compact ? 10 : 16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(theme.palette.glass)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(theme.palette.glassBorder, lineWidth: 1)
        )
    }
}

public struct SectionLabel: View {
    let title: String
    @ObservedObject private var theme = ThemeManager.shared
    
    public init(_ title: String) {
        self.title = title
    }
    
    public var body: some View {
        Text(title.uppercased())
            .font(.field(size: 11, weight: .bold))
            .foregroundColor(theme.palette.bronze)
            .tracking(1.2)
    }
}

public struct MetricRow: View {
    let label: String
    let value: String
    @ObservedObject private var theme = ThemeManager.shared
    
    public init(_ label: String, _ value: String) {
        self.label = label
        self.value = value
    }
    
    public var body: some View {
        HStack {
            Text(label)
                .font(.field(size: 14))
                .foregroundColor(theme.palette.mist)
            Spacer()
            Text(value)
                .font(.field(size: 15, weight: .semibold))
                .foregroundColor(theme.palette.cream)
        }
        .padding(.vertical, 2)
    }
}

public struct HuntButton: View {
    let title: String
    let icon: String?
    let action: () -> Void
    @ObservedObject private var theme = ThemeManager.shared
    
    public init(_ title: String, icon: String? = nil, action: @escaping () -> Void) {
        self.title = title
        self.icon = icon
        self.action = action
    }
    
    public var body: some View {
        Button(action: {
            SoundManager.shared.play(.click)
            action()
        }) {
            HStack(spacing: 8) {
                if let icon = icon {
                    Image(systemName: icon)
                }
                Text(title)
                    .font(.field(size: 15, weight: .semibold))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(theme.palette.bronze)
            )
            .foregroundColor(theme.palette.charcoal)
        }
    }
}

public struct ScreenBackground<Content: View>: View {
    let content: Content
    @ObservedObject private var theme = ThemeManager.shared
    
    public init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }
    
    public var body: some View {
        ZStack {
            theme.palette.forestDeep
                .ignoresSafeArea()
            content
        }
    }
}

/// Single source of truth for the version shown in About and Settings, so the
/// displayed build tracks Info.plist instead of drifting from it.
public enum AppVersion {
    public static var short: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.1.30"
    }

    public static var build: String {
        Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "43"
    }

    public static var display: String {
        "\(short) (\(build))"
    }
}
