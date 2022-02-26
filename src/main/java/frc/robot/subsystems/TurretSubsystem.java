package frc.robot.subsystems;

import com.ctre.phoenix.motorcontrol.LimitSwitchNormal;
import com.ctre.phoenix.motorcontrol.LimitSwitchSource;
import com.ctre.phoenix.motorcontrol.NeutralMode;
import com.ctre.phoenix.motorcontrol.TalonFXControlMode;
import com.ctre.phoenix.motorcontrol.can.TalonFX;

import edu.wpi.first.math.util.Units;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.wpilibj.PneumaticHub;
import edu.wpi.first.wpilibj.Solenoid;
import edu.wpi.first.wpilibj.shuffleboard.Shuffleboard;
import edu.wpi.first.wpilibj.shuffleboard.ShuffleboardTab;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants;

public class TurretSubsystem extends SubsystemBase {

    enum HoodState { UP, DOWN }

    class LimelightData {

        public double x, y, area = 0.0;
        public boolean isAligned, isTargeting = false;
        public int currentPipeline = 0;
    
        public LimelightData () {}
    
        public double getDistance (HoodState state) {
            if (!isTargeting) return 0.0;
            else {
                double H1_LIMELIGHT_HEIGHT_HUP      = Units.metersToInches(1.1176); // 44 in
                double H1_LIMELIGHT_HEIGHT_HDOWN    = Units.metersToInches(1.0668); // 42 in
                double H2_GOAL_HEIGHT = Units.metersToInches(2.591); // 8.5 ft
                double A1_HOOD_UP   = Units.degreesToRadians(16.0);
                double A1_HOOD_DOWN = Units.degreesToRadians(32.0);
                double A2_Y_DIFF = this.y;
    
                double A1;
                switch (state) {
                    case UP:
                        A1 = A1_HOOD_UP;
                        break;
    
                    case DOWN:
                        A1 = A1_HOOD_DOWN;
                        break;
    
                    default:
                        A1 = A1_HOOD_UP;
                        break;
                }

                double H1;
                switch (state) {
                    case UP:
                        H1 = H1_LIMELIGHT_HEIGHT_HUP;
                        break;

                    case DOWN:
                        H1 = H1_LIMELIGHT_HEIGHT_HDOWN;
                        break;

                    default:
                        H1 = H1_LIMELIGHT_HEIGHT_HUP;
                        break;
                }
    
                // (h2 - h1) / Math.tan(a1 + a2)
                double distance = (H2_GOAL_HEIGHT - H1) / Math.tan(A1 + A2_Y_DIFF);
                return Math.abs(distance);
            }
        }
    }

    private static final double TURRET_MAX_TICKS = 25_000.0;

    private TalonFX motor = new TalonFX(Constants.Turret.MOTOR_ID);

    private HoodState currentHoodState = HoodState.UP;
    private Solenoid hood;

    private LimelightData limelightData = new LimelightData();
    private NetworkTable table = NetworkTableInstance.getDefault().getTable("limelight");
    
    public TurretSubsystem (PneumaticHub hub) {
        hood = hub.makeSolenoid(Constants.Turret.HOOD_ID);

        motor.configFactoryDefault();

        // motor.setNeutralMode(NeutralMode.Brake);
        motor.setNeutralMode(NeutralMode.Coast);

        //? limit switch "hard stops"
        motor.configForwardLimitSwitchSource(LimitSwitchSource.FeedbackConnector, LimitSwitchNormal.NormallyOpen);
        motor.configReverseLimitSwitchSource(LimitSwitchSource.FeedbackConnector, LimitSwitchNormal.NormallyOpen);

        //? pid for motion magic
        motor.config_kP(0, 1.0);
        motor.config_kI(0, 0.0);
        motor.config_kD(0, 0.0);
        motor.config_kF(0, 0.2);

        //? velocity and acceleration for motion magic
        motor.configMotionCruiseVelocity(10_000.0);
        motor.configMotionAcceleration(7_000.0);

        this.reset();
        dashboard();
    }

    /* 
    * ####################### 
    *   Motor Functions
    * #######################
    */
    private void reset () { motor.setSelectedSensorPosition(0.0); }

    public void stop () { motor.set(TalonFXControlMode.PercentOutput, 0.0); }

    public void setPosition (double pos) {
        if ((Math.abs(pos) <= TURRET_MAX_TICKS)) {
            motor.set(TalonFXControlMode.MotionMagic, pos);
        }
    }

    public void setPower (double power) {
        if ((Math.abs(motor.getSelectedSensorPosition()) <= TURRET_MAX_TICKS)) {
            motor.set(TalonFXControlMode.PercentOutput, power);
        }
    }


    /* 
    * ####################### 
    *   Hood Functions
    * #######################
    */
    private void setHoodState (HoodState state) {
        currentHoodState = state;
        switch (state) {
            case UP:
                hood.set(true);
                break;

            case DOWN:
                hood.set(false);
                break;
        }
    }
    public void putHoodUp () { setHoodState(HoodState.UP); }
    public void putHoodDown () { setHoodState(HoodState.DOWN); }


    /* 
    * ####################### 
    *   Limelight Functions
    * #######################
    */
    private void limelightPeriodic () {
        limelightData.x                 = table.getEntry("tx").getDouble(0.0);
        limelightData.y                 = table.getEntry("ty").getDouble(0.0);
        limelightData.area              = table.getEntry("ta").getDouble(0.0);
        limelightData.isTargeting       = table.getEntry("tv").getNumber(0).intValue() == 1 ? true : false;
        limelightData.currentPipeline   = table.getEntry("pipeline").getNumber(0).intValue();

        limelightData.isAligned = Math.abs(limelightData.x) < 0.1 ? true : false;
    }

    public double getXError () {
        return limelightData.x;
    }

    public double calculateShooterRPM () {
        double m = 0.5;
        double b = 1000.0;
        return (m * limelightData.getDistance(currentHoodState)) + b;
    }

    @Override
    public void periodic() {
        limelightPeriodic();
    }

    private void dashboard () {
        ShuffleboardTab tab = Shuffleboard.getTab("Turret");
        tab.add(this);
        tab.addNumber("Turret Sensor Data", motor::getSelectedSensorPosition);
        tab.addNumber("Limelight Horiz Error ", this::getXError);
        tab.addNumber("Calculated Shooter RPM", this::calculateShooterRPM);
        tab.addNumber("Limelight Distance", () -> limelightData.getDistance(currentHoodState));
    }

}
