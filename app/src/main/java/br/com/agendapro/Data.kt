package br.com.agendapro

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity data class Client(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val birthday: String = "", // dd/MM
    val notes: String = "",
    val loyaltyRedeemed: Int = 0
)

@Entity data class Service(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val price: Double,
    val durationMin: Int = 60
)

@Entity data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val price: Double,
    val cost: Double = 0.0,
    val commissionPct: Double = 0.0
)

@Entity data class Staff(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val commissionPct: Double = 0.0
)

@Entity data class Appointment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    val serviceId: Long,
    val date: String, // yyyy-MM-dd
    val time: String, // HH:mm
    val durationMin: Int = 60,
    val price: Double = 0.0,
    val status: String = "AGENDADO", // AGENDADO, CONFIRMADO, CONCLUIDO, CANCELADO
    val paid: Boolean = false,
    val payMethod: String = "",
    val notes: String = "",
    val staffId: Long = 0,
    val productId: Long = 0,
    val productQty: Int = 0,
    val productTotal: Double = 0.0,
    val productCost: Double = 0.0,
    val packageId: Long = 0 // id do ClientPackage usado (0 = nenhum)
)

val Appointment.total: Double get() = price + productTotal

@Entity data class PackageDef(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val serviceId: Long,
    val sessions: Int,
    val price: Double
)

@Entity data class ClientPackage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    val packageName: String,
    val serviceId: Long,
    val total: Int,
    val used: Int = 0,
    val price: Double,
    val date: String, // yyyy-MM-dd (data da venda)
    val payMethod: String = ""
)

@Entity data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val amount: Double,
    val date: String,
    val recurringMonthly: Boolean = false
)

@Entity data class WaitItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    val note: String = ""
)

@Entity data class Holiday(
    @PrimaryKey val date: String, // yyyy-MM-dd
    val name: String
)

data class ClientCount(val clientId: Long, val n: Int)

@Dao interface ClientDao {
    @Query("SELECT * FROM Client ORDER BY name COLLATE NOCASE") fun all(): Flow<List<Client>>
    @Query("SELECT * FROM Client") suspend fun list(): List<Client>
    @Upsert suspend fun upsert(c: Client)
    @Upsert suspend fun upsertAll(l: List<Client>)
    @Delete suspend fun delete(c: Client)
}

@Dao interface ServiceDao {
    @Query("SELECT * FROM Service ORDER BY name COLLATE NOCASE") fun all(): Flow<List<Service>>
    @Query("SELECT * FROM Service") suspend fun list(): List<Service>
    @Upsert suspend fun upsert(s: Service)
    @Upsert suspend fun upsertAll(l: List<Service>)
    @Delete suspend fun delete(s: Service)
}

@Dao interface ProductDao {
    @Query("SELECT * FROM Product ORDER BY name COLLATE NOCASE") fun all(): Flow<List<Product>>
    @Query("SELECT * FROM Product") suspend fun list(): List<Product>
    @Upsert suspend fun upsert(p: Product)
    @Upsert suspend fun upsertAll(l: List<Product>)
    @Delete suspend fun delete(p: Product)
}

@Dao interface StaffDao {
    @Query("SELECT * FROM Staff ORDER BY name COLLATE NOCASE") fun all(): Flow<List<Staff>>
    @Query("SELECT * FROM Staff") suspend fun list(): List<Staff>
    @Upsert suspend fun upsert(s: Staff)
    @Upsert suspend fun upsertAll(l: List<Staff>)
    @Delete suspend fun delete(s: Staff)
}

@Dao interface AppointmentDao {
    @Query("SELECT * FROM Appointment WHERE date = :d ORDER BY time") fun byDate(d: String): Flow<List<Appointment>>
    @Query("SELECT * FROM Appointment WHERE date = :d AND status != 'CANCELADO'") suspend fun activeByDate(d: String): List<Appointment>
    @Query("SELECT * FROM Appointment WHERE date BETWEEN :a AND :b ORDER BY date, time") fun between(a: String, b: String): Flow<List<Appointment>>
    @Query("SELECT clientId, COUNT(*) AS n FROM Appointment WHERE status = 'CONCLUIDO' GROUP BY clientId") fun concludedCounts(): Flow<List<ClientCount>>
    @Query("SELECT * FROM Appointment") suspend fun list(): List<Appointment>
    @Upsert suspend fun upsert(a: Appointment)
    @Upsert suspend fun upsertAll(l: List<Appointment>)
    @Delete suspend fun delete(a: Appointment)
}

@Dao interface PackageDefDao {
    @Query("SELECT * FROM PackageDef ORDER BY name COLLATE NOCASE") fun all(): Flow<List<PackageDef>>
    @Query("SELECT * FROM PackageDef") suspend fun list(): List<PackageDef>
    @Upsert suspend fun upsert(p: PackageDef)
    @Upsert suspend fun upsertAll(l: List<PackageDef>)
    @Delete suspend fun delete(p: PackageDef)
}

@Dao interface ClientPackageDao {
    @Query("SELECT * FROM ClientPackage ORDER BY date DESC, id DESC") fun all(): Flow<List<ClientPackage>>
    @Query("SELECT * FROM ClientPackage") suspend fun list(): List<ClientPackage>
    @Query("SELECT * FROM ClientPackage WHERE id = :id") suspend fun get(id: Long): ClientPackage?
    @Upsert suspend fun upsert(p: ClientPackage)
    @Upsert suspend fun upsertAll(l: List<ClientPackage>)
    @Delete suspend fun delete(p: ClientPackage)
}

@Dao interface ExpenseDao {
    @Query("SELECT * FROM Expense ORDER BY date DESC") fun all(): Flow<List<Expense>>
    @Query("SELECT * FROM Expense") suspend fun list(): List<Expense>
    @Upsert suspend fun upsert(e: Expense)
    @Upsert suspend fun upsertAll(l: List<Expense>)
    @Delete suspend fun delete(e: Expense)
}

@Dao interface WaitDao {
    @Query("SELECT * FROM WaitItem ORDER BY id") fun all(): Flow<List<WaitItem>>
    @Query("SELECT * FROM WaitItem") suspend fun list(): List<WaitItem>
    @Upsert suspend fun upsert(w: WaitItem)
    @Upsert suspend fun upsertAll(l: List<WaitItem>)
    @Delete suspend fun delete(w: WaitItem)
}

@Dao interface HolidayDao {
    @Query("SELECT * FROM Holiday ORDER BY date") fun all(): Flow<List<Holiday>>
    @Query("SELECT * FROM Holiday") suspend fun list(): List<Holiday>
    @Upsert suspend fun upsert(h: Holiday)
    @Upsert suspend fun upsertAll(l: List<Holiday>)
    @Delete suspend fun delete(h: Holiday)
}

@Database(
    entities = [Client::class, Service::class, Product::class, Staff::class, Appointment::class,
        PackageDef::class, ClientPackage::class, Expense::class, WaitItem::class, Holiday::class],
    version = 2, exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun clients(): ClientDao
    abstract fun services(): ServiceDao
    abstract fun products(): ProductDao
    abstract fun staff(): StaffDao
    abstract fun appointments(): AppointmentDao
    abstract fun packageDefs(): PackageDefDao
    abstract fun clientPackages(): ClientPackageDao
    abstract fun expenses(): ExpenseDao
    abstract fun waitlist(): WaitDao
    abstract fun holidays(): HolidayDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "agenda.db")
                .fallbackToDestructiveMigration()
                .build().also { inst = it }
        }
    }
}
