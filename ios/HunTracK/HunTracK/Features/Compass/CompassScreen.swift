import SwiftUI

public struct CompassScreen: View {
    @ObservedObject private var compass = CompassManager.shared
    @ObservedObject private var location = LocationManager.shared
    @ObservedObject private var theme = ThemeManager.shared
    
    public init() {}
    
    public var body: some View {
        ScreenBackground {
            VStack(spacing: 24) {
                Text("Compass")
                    .font(.field(size: 28, weight: .bold))
                    .foregroundColor(theme.palette.cream)
                    .padding(.top, 16)
                
                // Vintage Dial Graphic
                ZStack {
                    Circle()
                        .stroke(theme.palette.bronze, lineWidth: 6)
                        .frame(width: 260, height: 260)
                    
                    Circle()
                        .fill(theme.palette.charcoal)
                        .frame(width: 244, height: 244)
                    
                    // Rotating Needle
                    VStack(spacing: 0) {
                        Triangle()
                            .fill(Color(hex: "#C62828"))
                            .frame(width: 24, height: 90)
                        Triangle()
                            .fill(theme.palette.slate)
                            .frame(width: 24, height: 90)
                            .rotationEffect(.degrees(180))
                    }
                    .rotationEffect(.degrees(Double(-compass.heading.magneticHeading)))
                    .animation(.easeOut(duration: 0.1), value: compass.heading.magneticHeading)
                    
                    Circle()
                        .fill(theme.palette.bronzeBright)
                        .frame(width: 16, height: 16)
                }
                
                Text("\(Int(compass.heading.magneticHeading))°")
                    .font(.field(size: 36, weight: .bold))
                    .foregroundColor(theme.palette.cream)
                
                GlassPanel {
                    MetricRow("Magnetic North", "\(Int(compass.heading.magneticHeading))°")
                    MetricRow("True North", "\(Int(compass.heading.trueHeading))°")
                    if let speed = location.latestFix?.speedMps, speed > 0.8, let course = location.latestFix?.bearingDegrees {
                        MetricRow("GPS Course", "\(Int(course))°")
                    } else {
                        MetricRow("GPS Course", "Walk to acquire")
                    }
                }
                .padding(.horizontal, 16)
                
                Spacer()
            }
        }
        .navigationBarTitleDisplayMode(.inline)
    }
}

public struct Triangle: Shape {
    public func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.midX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}
