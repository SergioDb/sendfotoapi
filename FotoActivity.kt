//получение данных с сервера и отправка фотографий на сервер
class FotoActivity : AppCompatActivity() {

    @Inject
    lateinit var remoteRepository: RemoteRepository

    @Inject
    lateinit var localRepository: LocalRepository
    internal var mCompositeDisposable: CompositeDisposable? = null

    var id_sender: Int = 0

    internal var dialog: AlertDialog? = null
    lateinit var sp: SharedPreferences

    var listFotoA:List<FOTO_A_VIEW>? = null
    var listFotoAFiles:MutableList<FOTO_A>? = null

    lateinit var listViewFotoA: ListView
    lateinit var listViewFotoFiles: ListView
    var Tag:String? = null
    var curr_pos : Int = 0

    val formatTime = SimpleDateFormat("HHmmss")
    private val listFotoAFilesLock = ReentrantLock()
    lateinit var textBottom : TextView
    lateinit var bottomSheet : View
    lateinit var BtnBottomSheet : LinearLayout
    lateinit var BtndBottomSheetText : TextView
    lateinit var ProgressBar : ContentLoadingProgressBar

    override fun onBackPressed() {
        if (listFotoAFiles != null)
        {
            if (listFotoAFiles!!.filter { x -> x.IS_SAVED ==0 && x.ID != null }.size >0) {
                val quitDialog = Dialog_OkCansel("Продолжите действие", "Не все фотографии сохранены" +
                        "!",
                        "Закрыть", null, object : Dialog_OkCansel.CommunicatorDialogInfo {
                    override fun BtnDialogCansel() {
                    }
                    override fun BtnDialogOkClick() {
                        finish()
                    }
                })
                quitDialog!!.show(fragmentManager, "Dialog_OkCansel")
                //диалог вопроса
                //при ответе да не упскаем на выход
            }
            else
                super.onBackPressed()
        }
        else
        super.onBackPressed()

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_FOTO_A)
        mCompositeDisposable = CompositeDisposable()
        App.getAppComponent().inject(this)
        supportActionBar?.title = "Выбор для фотохранилища"
        sp = PreferenceManager.getDefaultSharedPreferences(this)

        try {
            id_sender = sp.getInt("sender_id", 390)
        } catch (e: Exception) {
            id_sender = 390
        }

        textBottom = findViewById(R.id.textBottom) as TextView
        bottomSheet = findViewById(R.id.foto_bottom_sheet) as View
        val bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.isHideable = false;
        bottomSheetBehavior.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        textBottom.text = "Потяните вниз"
                        listViewFotoA.isEnabled = false

                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        textBottom.text = "Потяните вверх"
                        listViewFotoA.isEnabled = true

                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {

            }
        })



        BtnBottomSheet = findViewById(R.id.btnFotoRetry) as LinearLayout
        BtndBottomSheetText = findViewById(R.id.btnFotoRetryText) as TextView
        listViewFotoA = findViewById(R.id.list_FOTO_A) as ListView
        listViewFotoFiles = findViewById(R.id.list_file_action) as ListView
        ProgressBar = findViewById(R.id.progress_file_action) as ContentLoadingProgressBar

        BtndBottomSheetText.setOnClickListener(View.OnClickListener { retry_upload() })

        listViewFotoA.setOnItemClickListener(AdapterView.OnItemClickListener { adapterView, view, position, l ->

            if (listFotoA != null)
                if (!listFotoA!!.isEmpty()) {

                    try {
                        curr_pos = position
                        listFotoAFilesLock.lock()
                        if (listFotoAFiles.isNullOrEmpty())
                            listFotoAFiles = mutableListOf()

                        var item  = FOTO_A(
                                        ID  = null,
                                        ID_M_FOTO_A  = listFotoA?.get(position)?.ID_M_FOTO_A,
                                        NAME  =  listFotoA?.get(position)?.NAME,
                                        TM_NAME  =  listFotoA?.get(position)?.TM_NAME,
                                        IS_SAVED  = 0,
                                        PATH  = null,
                                        FOTO_NAME  = null,
                                        id_sender   = id_sender
                                )
                        val storageState = Environment.getExternalStorageState()
                        if (storageState == Environment.MEDIA_MOUNTED) {

                            val name = UUID.randomUUID().toString() + ".jpg"

                            val path = this.getExternalFilesDir("Pictures").absolutePath + File.separator+"bitx"+ File.separator + name
                            var _photoFile = File(path)
                            try {
                                if (_photoFile.exists() === false) {
                                    if (!_photoFile.getParentFile().mkdirs() && !_photoFile.getParentFile().isDirectory()) {
                                        Log.d(this.javaClass.simpleName, "Directory not created");
                                    } else {
                                        Log.d(this.javaClass.simpleName, "Directory created");
                                    }
                                    _photoFile.createNewFile()
                                }

                            } catch (e1: IOException) {
                                e1.printStackTrace()
                                Log.d(this.javaClass.simpleName, e1.message)
                            }

                            Log.d(this.javaClass.simpleName, path)

                            var _fileUri = 
                                    FileProvider.getUriForFile(this, this.getApplicationContext().getPackageName() + ".provider", _photoFile);
                            item.PATH = path
                            item.FOTO_NAME = id_sender.toString()+"_"+formatTime.format(Calendar.getInstance().time)+".jpg"
                            listFotoAFiles!!.add(item)
                           
                            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                            intent.putExtra(MediaStore.EXTRA_OUTPUT, _fileUri)
                            startActivityForResult(intent, REQUEST_TAKE_PHOTO)

                        } else {
                            AlertDialog.Builder(this)
                                    .setMessage("External Storeage (SD Card) is required.\n\nCurrent state: $storageState")
                                    .setCancelable(true).create().show()
                            return@OnItemClickListener
                        }

                    } catch (e: Exception) {
                        e.printStackTrace()

                    }
                    finally {
                        listFotoAFilesLock.unlock()
                    }


                }
        }
        )

        UpdateListFile()
    }

    override  fun onResume() {
        super.onResume()

            isOnline()
            taskGetFotoA()

    }

    fun isOnline(): Boolean {
        if (sp == null)
            sp = PreferenceManager.getDefaultSharedPreferences(this)
        val prefsEditor = sp.edit()
        try {

            val cm = this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val netInfo = cm.getActiveNetworkInfo();

            if (netInfo != null && (netInfo.isConnected() || netInfo.getDetailedState() == NetworkInfo.DetailedState.CONNECTING)) {
                prefsEditor.putInt("typesend", 0)
                prefsEditor.commit()
                return true
            } else {
                prefsEditor.putInt("typesend", 1)
                prefsEditor.commit()
                return false
            }


        } catch (e: Exception) {
            prefsEditor.putInt("typesend", 0)
            prefsEditor.commit()
            return true
        }

    }
    override fun onDestroy() {
        super.onDestroy()

        mCompositeDisposable?.clear()
    }


    fun selectListActionView()
    {  try {
        val localDao = localRepository
        val disposable2 = localDao!!.selectFotoActonView(id_sender)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError {
                    Log.d(this.javaClass.simpleName, "getcurrent selectFotoActonView  error:" + it.message)
                    val quitDialog = Dialog_Error("Ошибка", "Список  пуст",
                            "Ок", object : Dialog_Error.CommunicatorDialogEInfo {
                        override fun BtnDialogCansel() {
                            dismissProgressDialog(dialog)
                            finish()
                        }


                    }
                    )
                    quitDialog!!.show(fragmentManager, "Dialog_Error")
                }
                .subscribe({ v ->
                    dismissProgressDialog(dialog)
                    if (v != null) {

                        //тут мы должны заполнить список и обновить
                        listFotoA = v!!
                        var adapterFotoA = FotoAAdapter(this@FotoActivity, listFotoA)
                        listViewFotoA!!.setAdapter(adapterFotoA)
                        adapterFotoA.notifyDataSetChanged()

                    }

                }, { e ->
                    e.printStackTrace()
                    Log.d(this.javaClass.simpleName, "getcurrent ScheduleList  error:" + e.message)
                })

        mCompositeDisposable?.add(disposable2)
    }
    catch (e:java.lang.Exception)
    {e.printStackTrace()}
    }

    fun taskGetFotoA() {
        val remoteDao = remoteRepository
        remoteDao.create_(applicationContext)
        //добавить сохранение списка в базу запрашивать будем только 1 раз если список пустой
        val localDao = localRepository

        if (listFotoA.isNullOrEmpty()) {
            val sequential = Schedulers.from(Executors.newCachedThreadPool())
            isOnline()
            setProgressDialog("Запрашиваем список фотоакций")

            val disposable =
                    remoteDao!!.requestGetFotoA(FOTO_A_VIEW_REQUEST(number_sender = id_sender))
                            .subscribeOn(sequential)
                            .observeOn(AndroidSchedulers.mainThread())
                            .doOnError { t1 ->

                                selectListActionView()
                                Log.d(this.javaClass.simpleName, t1.message)

                            }
                            .doFinally {
                                dismissProgressDialog(dialog)
                                isOnline()
                            }
                            .retryWhen { throwableObservable -> throwableObservable.take(3).delay(5, TimeUnit.SECONDS) }
                            .subscribe({ v ->
                                dismissProgressDialog(dialog)
                                if (v != null) {
                                    Log.d(this.javaClass.simpleName, "getcurrent  ok")
                                    if (v.message.status != 0) {
                                        Log.d(this.javaClass.simpleName, "getcurrent   error:" + v.message.text)
                                        visibleToastCentre(v.message.text!!)
                                    } else
                                        if (v.model!!.size == 0) {
                                            val quitDialog = Dialog_Error("Ошибка", "Список пуст",
                                                    "Ок", object : Dialog_Error.CommunicatorDialogEInfo {
                                                override fun BtnDialogCansel() {
                                                    dismissProgressDialog(dialog)

                                                }
                                            }
                                            )
                                            quitDialog!!.show(fragmentManager, "Dialog_Error")

                                        } else {
                                            //тут мы должны заполнить список и обновить
                                            try {
                                                localDao!!.insertFotoActonView(v.model!!, id_sender)
                                            }
                                            catch (e1: Exception)
                                            {e1.printStackTrace()}
                                            listFotoA = v.model!!
                                            var adapterFotoA = FotoAAdapter(this@FotoActivity, listFotoA)
                                            listViewFotoA!!.setAdapter(adapterFotoA)
                                            adapterFotoA.notifyDataSetChanged()

                                        }
                                }
                            }, { e ->
                                e.printStackTrace()
                                Log.d(this.javaClass.simpleName, "getcurrent error:" + e.message)
                            })

            mCompositeDisposable?.add(disposable)
        }
        else
            selectListActionView()
    }


    fun visibleToastCentre(mes: String) {
        Log.d(this.javaClass.simpleName, " answer $mes")
        try {
            val toast = Toast.makeText(applicationContext,
                    mes, Toast.LENGTH_SHORT)
            toast.setGravity(Gravity.CENTER, 0, 0)
            toast.show()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d(this.javaClass.simpleName, "error: " + e.message)
        }

    }

    fun dismissProgressDialog(dialog: AlertDialog?) {
        var dialog = dialog
        if (dialog != null) {
            if (dialog.isShowing) {
                val context = (dialog.context as ContextWrapper).baseContext
                if (context is Activity) {
                    // Api >=17
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        if (!context.isFinishing && !context.isDestroyed) {
                            dismissWithExceptionHandling(dialog)
                        }
                    } else {

                        // Api < 17. 
                        if (!context.isFinishing) {
                            dismissWithExceptionHandling(dialog)
                        }
                    }
                } else
                     dismissWithExceptionHandling(dialog)
            }
            dialog = null
        }
    }

    fun setProgressDialog(aText: String) {

        var llPadding = 30;
        var ll = LinearLayout(this);
        ll.setOrientation(LinearLayout.HORIZONTAL);
        ll.setPadding(llPadding, llPadding, llPadding, llPadding);
        ll.setGravity(Gravity.CENTER);
        var llParam = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        llParam.gravity = Gravity.CENTER;
        ll.setLayoutParams(llParam);

        var progressBar = ProgressBar(this);
        progressBar.setIndeterminate(true);
        progressBar.setPadding(0, 0, llPadding, 0);
        progressBar.setLayoutParams(llParam);

        llParam = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        llParam.gravity = Gravity.CENTER;
        var tvText = TextView(this);
        tvText.setText(aText);
        tvText.setTextColor(Color.parseColor("#000000"));
        tvText.setTextSize(20F);
        tvText.setLayoutParams(llParam);

        ll.addView(progressBar);
        ll.addView(tvText);

        var builder = AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setView(ll);
        dialog = builder.create();
        dialog!!.show();
        var window = dialog!!.getWindow();
        if (window != null) {
            var layoutParams = WindowManager.LayoutParams();
            layoutParams.copyFrom(dialog!!.getWindow().getAttributes());
            layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            dialog!!.getWindow().setAttributes(layoutParams);
        }

    }

    fun dismissWithExceptionHandling(dialog: AlertDialog?) {
        var dialog = dialog
        try {
            dialog!!.dismiss()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            dialog = null
        }
    }


    fun getEncoded64ImageStringFromBitmap(bitmap: String?): String? {
        if (bitmap != null) {
            val IMAGE_MAX_SIZE = 1200000;
            var bm = BitmapFactory.decodeFile(bitmap);
            val height = bm.getHeight();
            val width = bm.getWidth();

            val y: Double = Math.sqrt(IMAGE_MAX_SIZE / (width.toDouble() / height))
            val x: Double = y / height.toDouble() * width.toDouble()

            val scaledBitmap = Bitmap.createScaledBitmap(bm, x.toInt(),
                    y.toInt(), true)
            bm.recycle()
            bm = scaledBitmap
            System.gc()
            var stream = ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.JPEG, 80, stream);
            var byteFormat = stream.toByteArray();
            return Base64.encodeToString(byteFormat, Base64.DEFAULT)

        }
        return null;
    }

     fun requestMultiplePermissions() {
        ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                1)
    }

    fun deleteFolder(folder: File) {
        if (folder.isDirectory()) {
            for (ct in folder.listFiles()) {
                deleteFolder(ct);
            }
        }
        if (!folder.delete()) {
            throw FileNotFoundException("Unable to delete: " + folder);
        }
    }

    fun uploadImage (item : FOTO_A)
    {   try {
        listFotoAFilesLock.lock()
        val i = listFotoAFiles?.indexOf(item)
        if (i != null) {
            listFotoAFiles?.get(i)?.PROGRESS = true
            item.PROGRESS = true
        }
        (listViewFotoFiles!!.adapter as FotoFilesAdapter).notifyDataSetChanged()
         }
        finally {
            listFotoAFilesLock.unlock()
        }
        isOnline()
        val remoteDao = remoteRepository
        remoteDao.create_(applicationContext)
        //сохраняем фото
       // setProgressDialog("Сохраняем фотографию")
        var ss: SaveStoresPhotoReportsResponse = SaveStoresPhotoReportsResponse()
        ss.PATH = listOf(item.NAME, item.TM_NAME)
        ss.FILES = mapOf(item.FOTO_NAME to getEncoded64ImageStringFromBitmap(item.PATH))

       val disposable =   remoteDao!!.requestSaveStoresPhotoReports(ss)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnError { t1 ->
                            Log.d(this.javaClass.simpleName, t1.message)

                        }
                        .retryWhen { throwableObservable -> throwableObservable.take(3).delay(5, TimeUnit.SECONDS) }
                        .subscribe({ v ->

                            if (v != null) {
                                Log.d(this.javaClass.simpleName, "getcurrent ok")
                                if (v.status != 0) {
                                    Log.d(this.javaClass.simpleName, "getcurrent error:" + v.text)
                                    visibleToastCentre(v.text!!)
                                    /*можно именить значек на ошибку*/
                                } else {
                                    try {
                                        listFotoAFilesLock.lock()
                                        var i = listFotoAFiles?.indexOf(item)
                                        item.IS_SAVED = 1
                                        item.PROGRESS = false
                                        localRepository.updateFotoActon(item, id_sender)
                                        if (i != null) {
                                            listFotoAFiles?.get(i)?.IS_SAVED = 1
                                            listFotoAFiles?.get(i)?.PROGRESS = false
                                        }

                                        (listViewFotoFiles!!.adapter as FotoFilesAdapter).notifyDataSetChanged()

                                        try {
                                            deleteFolder(File(item.PATH))
                                            BtndBottomSheetText.text = listFotoAFiles?.filter { x -> x.IS_SAVED == 0 }?.toList()?.size.toString()
                                        }
                                        catch (e:java.lang.Exception)
                                        {
                                            e.printStackTrace()
                                        }

                                        visibleToastCentre("Фотография успешно сохранена")

                                    }
                                    finally {
                                        listFotoAFilesLock.unlock()
                                    }
                                }
                            }
                        }, { e ->
                            e.printStackTrace()
                            Log.d(this.javaClass.simpleName, "getcurrent error:" + e.message)
                        })



        mCompositeDisposable?.add(disposable)


    }
    fun retry_upload()
    {
        try {
            if (listFotoAFiles.isNullOrEmpty()) return
            visibleToastCentre("Сохранение фотографий...")
            ProgressBar.visibility = View.VISIBLE
            BtnBottomSheet.isEnabled = false
            val sequential = Schedulers.from(Executors.newCachedThreadPool())
            val disposable = Flowable.fromIterable(listFotoAFiles)
                    .subscribeOn(sequential)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnError { t1 ->
                        Log.d(this.javaClass.simpleName, t1.message)

                    }
                    .doOnComplete {
                        ProgressBar.visibility = View.GONE
                        BtnBottomSheet.isEnabled = true
                    }
                    .retryWhen { throwableObservable -> throwableObservable.take(3).delay(5, TimeUnit.SECONDS) }
                    .forEach(this::uploadImage)

            mCompositeDisposable?.add(disposable)
        }
        catch (e:java.lang.Exception)
        {e.printStackTrace()}
        return
    }
    fun UpdateListFile()
    {
        val localDao = localRepository
        val disposable = localDao!!.selectFotoActon(id_sender)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError { t ->
                    Log.d(this.javaClass.simpleName, t.message)
                }
                .retryWhen{throwableObservable -> throwableObservable.take(3).delay(2, TimeUnit.SECONDS)}
                .subscribe( { v ->
                    if (v != null) {
                        /*вот тут как раз отображаем список*/
                        try {
                            listFotoAFilesLock.lock()

                            if (listViewFotoA!!.adapter != null)
                                listViewFotoA!!.adapter = null
                            listFotoAFiles = v.toMutableList()
                            BtndBottomSheetText.text = v.size.toString()

                            var adapterFotoFiles = FotoFilesAdapter(this@FotoActivity, listFotoAFiles)
                            listViewFotoFiles!!.setAdapter(adapterFotoFiles)
                            adapterFotoFiles.notifyDataSetChanged()

                        }
                        finally {
                            listFotoAFilesLock.unlock()
                        }
                    }
                }, { e ->
                    e.printStackTrace()
                    Log.d(this.javaClass.simpleName, "getcurrent listFotoAFiles  error:" + e.message)
                } )
        mCompositeDisposable?.add(disposable)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {

            REQUEST_TAKE_PHOTO -> {
                if (resultCode == Activity.RESULT_OK) {
                    var l = listFotoAFiles?.filter { x -> x.IS_SAVED == 0 && x.ID == null }?.toList()
                    if (l != null) {
                        val localDao = localRepository
                        localDao.insertFotoActon(l, id_sender)
                    }
                }
                UpdateListFile()
            }
        }
    }

    companion object {
        private val REQUEST_TAKE_PHOTO = 77
    }
}