package frc.robot.subsystems;

import java.util.function.DoubleSupplier;

import com.ctre.phoenix.sensors.Pigeon2;
import com.ctre.phoenix.sensors.PigeonIMU;

import frc.robot.Constants;
import frc.robot.Constants.SwerveDrivetrain.Mod0;
import frc.robot.utils.swerve.SwerveModule;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveDriveOdometry;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.wpilibj.shuffleboard.BuiltInWidgets;
import edu.wpi.first.wpilibj.shuffleboard.Shuffleboard;
import edu.wpi.first.wpilibj.shuffleboard.ShuffleboardTab;
import edu.wpi.first.wpilibj.smartdashboard.Field2d;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.SubsystemBase;


public class SwerveDrivetrain extends SubsystemBase {
    
    private SwerveDriveOdometry swerveOdometry;
    private SwerveModule[] swerveModules;
    private Pigeon2 gyro;
    private Field2d field;

    public SwerveDrivetrain() {
        this.gyro = new Pigeon2(Constants.SwerveDrivetrain.GYRO_ID);
        this.gyro.configFactoryDefault();
        this.zeroGyro();

        swerveModules = new SwerveModule[] {
            new SwerveModule(0, Constants.SwerveDrivetrain.Mod0.constants),
            new SwerveModule(1, Constants.SwerveDrivetrain.Mod1.constants),
            new SwerveModule(2, Constants.SwerveDrivetrain.Mod2.constants),
            new SwerveModule(3, Constants.SwerveDrivetrain.Mod3.constants)
        };


        //this.swerveOdometry = new SwerveDriveOdometry(Constants.SwerveDrivetrain.SWERVE_KINEMATICS, this.getYaw());
        this.swerveOdometry = new SwerveDriveOdometry(Constants.SwerveDrivetrain.SWERVE_KINEMATICS, this.getYaw(),
                                                    getModulePositions());
        //this.swerveOdometry = new SwerveDriveOdometry(kinematics: null, getYaw(), null)
        //this.swerveOdometry = new SwerveDriveOdometry(null, getYaw(), null, getPose())

        
        

        this.field = new Field2d();

        dashboard();
    }

    public void drive(Translation2d translation, double rotation, boolean fieldRelative, boolean isOpenLoop) {

        // Rotation2d angle = new Rotation2d(90.0);
        // SwerveModuleState modZero = new SwerveModuleState();
        // SwerveModuleState modOne = new SwerveModuleState(0, angle);
        // SwerveModuleState modTwo = new SwerveModuleState();
        // SwerveModuleState modThree = new SwerveModuleState();
        // SwerveModuleState[] zeroInit = new SwerveModuleState[]{modZero, modOne, modTwo, modThree};
        // //added 2/4/2023
        // setModuleStates(zeroInit);

        SwerveModuleState[] swerveModuleStates = Constants.SwerveDrivetrain.SWERVE_KINEMATICS.toSwerveModuleStates(
            fieldRelative ?
                ChassisSpeeds.fromFieldRelativeSpeeds(
                    translation.getX(),
                    translation.getY(),
                    rotation,
                    this.getYaw()
                )
            :
                new ChassisSpeeds(
                    translation.getX(),
                    translation.getY(),
                    rotation
                )
        );

        SwerveDriveKinematics.desaturateWheelSpeeds(swerveModuleStates, Constants.SwerveDrivetrain.MAX_SPEED);

        for (SwerveModule mod : this.swerveModules) {
            mod.setDesiredState(swerveModuleStates[mod.moduleNumber], isOpenLoop);
        }
    }

    /* Gyro */
    public void zeroGyro() {
        this.gyro.setYaw(0);
    }

    private double optimizeGyro (double degrees) {
        // 0 < degrees < 360
        if ((degrees > 0.0) && (degrees < 360.0)) {
            return degrees;
        } else {
            int m = (int) Math.floor( degrees / 360.0 );
            double optimizedDegrees = degrees - (m * 360.0);
            return Math.abs(optimizedDegrees);
        }
    }

    public Rotation2d getYaw() {
        double[] ypr = new double[3];
        this.gyro.getYawPitchRoll(ypr);
        double yaw = optimizeGyro(ypr[0]);
        return Constants.SwerveDrivetrain.INVERT_GYRO ? Rotation2d.fromDegrees(360 - yaw) : Rotation2d.fromDegrees(yaw);
    }

    public double getGyroAngleDegrees() {
        return this.getYaw().getDegrees();
    }

    public double getGyroAngleRadians() {
        return this.getYaw().getRadians();
    }

    /* Odometry */
    public Pose2d getPose() {
        return this.swerveOdometry.getPoseMeters();
    }

    public void setPose(Pose2d pose) {
        this.swerveOdometry.resetPosition(pose.getRotation(), getModulePositions(), pose);
    }

    public void resetOdometry(Pose2d pose) {
        this.swerveOdometry.resetPosition(this.getYaw(), getModulePositions() , pose);
    }

    /* Module States */
    public SwerveModuleState[] getStates() {
        SwerveModuleState[] states = new SwerveModuleState[4];
        for (SwerveModule mod : this.swerveModules) {
            states[mod.moduleNumber] = mod.getState();
        }
        return states;
    }

    public SwerveModulePosition[] getModulePositions(){
        SwerveModulePosition[] positions = new SwerveModulePosition[4];
        for(SwerveModule mod : swerveModules){
            positions[mod.moduleNumber] = mod.getPosition();
        }
        return positions;
    }

    public void setModuleStates(SwerveModuleState[] desiredStates) {
        SwerveDriveKinematics.desaturateWheelSpeeds(desiredStates, Constants.SwerveDrivetrain.MAX_SPEED);
        for (SwerveModule mod : this.swerveModules) {
            mod.setDesiredState(desiredStates[mod.moduleNumber], false);
        }
    }

    public void dashboard() {
        ShuffleboardTab tab = Shuffleboard.getTab("Drivetrain");
        tab.add(this);
        tab.addNumber("Gyro Angle ???", this::getGyroAngleDegrees).withWidget(BuiltInWidgets.kGyro);
        tab.addNumber("Gyro Angle (GRAPH) ???", this::getGyroAngleDegrees).withWidget(BuiltInWidgets.kGraph);
        SmartDashboard.putData(this.field);
        // SmartDashboard.putData("ANGLE PID", data);
        // SmartDashboard.putData("DRIVE PID", data);

        // tab.add("Mod0", Mod0.getCanCoder());
        // tab.addDouble("Mod0", (DoubleSupplier) swerveModules[0].getCanCoder());
        // tab.addDouble("Mod1", (DoubleSupplier) swerveModules[1].getCanCoder());
        // tab.addDouble("Mod2", (DoubleSupplier) swerveModules[2].getCanCoder());
        // tab.addDouble("Mod3", (DoubleSupplier) swerveModules[3].getCanCoder());

        tab.add("Mod0", swerveModules[0].getCanCoder2());
        tab.add("Mod1", swerveModules[1].getCanCoder2());
        tab.add("Mod2", swerveModules[2].getCanCoder2());
        tab.add("Mod3", swerveModules[3].getCanCoder2());


    }

    @Override
    public void periodic() {
        this.swerveOdometry.update(this.getYaw(), getModulePositions());
        this.field.setRobotPose(this.swerveOdometry.getPoseMeters());
    }
}
