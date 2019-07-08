
package com.khadijah.friendlychat;

import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;


import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;

    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;

    private String mUsername;

    private DatabaseReference mMessageDatabaseReference;
    private ChildEventListener mChildEventListener;

    //for user sign in and sign up
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mFirebaseAuthListener;
    public static final int RC_SIGN_IN = 1;

    private static final int RC_PHOTO_PICKER = 2;

    //to store images in fire base storage
    private StorageReference mChatStorageReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mUsername = ANONYMOUS;


        //get a reference to the root node(Firebase) in DB
        mMessageDatabaseReference = FirebaseDatabase.getInstance().getReference().child("messages");
        mChatStorageReference = FirebaseStorage.getInstance().getReference().child("chat_photos");

        //for sign in
        mFirebaseAuth = FirebaseAuth.getInstance();


        // Initialize references to views
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendButton = (Button) findViewById(R.id.sendButton);

        // Initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar non visible
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);


        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER);
            }
        });

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    //the user can send the msg if it is not null
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // ١- Send messages on click
                FriendlyMessage friendlyMessage = new FriendlyMessage(mMessageEditText.getText().toString(), mUsername, null);

                // ٢- (WRITE to firebase DB)
                /////// WRITE //////////
                mMessageDatabaseReference.push().setValue(friendlyMessage);

                // ٣- Clear input box
                mMessageEditText.setText("");


            }
        });


        //check the state for the user when he open the app
        mFirebaseAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                //Check if the user logged in or not
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    //user is signed in
                    onSignedIn(user.getDisplayName());

                    //Toast.makeText(MainActivity.this, "You're now signed in. Welcome to FriendlyChat.", Toast.LENGTH_SHORT).show();

                } else {
                    //user is signed out, So here we will lunch the sign in application flow
                    onSignedOut();

                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(true)
                                    .setAvailableProviders(Arrays.asList(
                                            new AuthUI.IdpConfig.EmailBuilder().build(),
                                            new AuthUI.IdpConfig.GoogleBuilder().build()))
                                    .build(),
                            RC_SIGN_IN);
                }
            }
        };


    }//end of onCreate()


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            //THIS MEAN THAT THE activity that we returned from is the singn in application
            //so check the result code coming from user
            if (resultCode == RESULT_OK) {
                Toast.makeText(MainActivity.this, "You're signed in!1.", Toast.LENGTH_SHORT).show();

            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(MainActivity.this, "Signed in was cancelled.", Toast.LENGTH_SHORT).show();
                finish(); //the activity
            } else if (requestCode == RC_PHOTO_PICKER && resultCode == RESULT_OK) {
                //handling the photo picker result
                Uri selectedImageUri = data.getData();//get the image uri

                // Get a reference to store file at this directory chat_photos/<FILENAME> in firebase STORAGE
                final StorageReference photoRef = mChatStorageReference.child(selectedImageUri.getLastPathSegment());

                // Upload file to Firebase STORAGE
                photoRef.putFile(selectedImageUri)
                        .addOnSuccessListener(this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
                            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                                // When the image has successfully uploaded, we get its download URL

                                photoRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                                    @Override
                                    public void onSuccess(Uri downloadUrl) {
                                        // Set the download URL to the message box, then directly that the user can send it to the database
                                        FriendlyMessage friendlyMessage = new FriendlyMessage(null, mUsername, downloadUrl.toString());
                                        //store the new object to DB
                                        mMessageDatabaseReference.push().setValue(friendlyMessage);

                                    }
                                });

                            }
                        });
            }
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (mFirebaseAuthListener != null) {

            mFirebaseAuth.removeAuthStateListener(mFirebaseAuthListener);
        }
        mMessageAdapter.clear();
        DetachDataBaseReadListener();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mFirebaseAuth.addAuthStateListener(mFirebaseAuthListener);
    }

    private void onSignedIn(String userName) {
        //1- set the user name to current signed in user name
        mUsername = userName.toString();

        AttachDataBaseReadListener();

    }

    private void onSignedOut() {
        //1- clear the user name
        mUsername = ANONYMOUS;

        //2- clear the messages list
        mMessageAdapter.clear();

        //3- detach the Listener
        DetachDataBaseReadListener();
    }

    private void AttachDataBaseReadListener() {
        //2- Read data from DB,LISTEN to the DB
        //3- Display it in the ListView

        if (mChildEventListener == null) {
            mChildEventListener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                    //if new msg added to db
                    // First: Read it, Second: Display it in the ListView

                    // (dataSnapshot contain data from DB and the location of this data)
                    //deserilize the Data come from the DB into FriendlyMessage Object
                    FriendlyMessage message = dataSnapshot.getValue(FriendlyMessage.class);
                    //  Log.e("************", message.getName());

                    //add the new msg to adapter -- UI
                    mMessageAdapter.addAll(message);
                }

                @Override
                public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                }

                @Override
                public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {
                }

                @Override
                public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                }
            };


            //4- attach the Listener

            //add the child listener to the DB reference
            //mean the listener here describe what happen
            // in the data inside the DB reference called "message"
            mMessageDatabaseReference.addChildEventListener(mChildEventListener);
        }
    }

    private void DetachDataBaseReadListener() {
        //stop reading
        if (mChildEventListener != null) {
            mMessageDatabaseReference.removeEventListener(mChildEventListener);
            mChildEventListener = null;
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sign_out_menu:
                //sign out here
                AuthUI.getInstance().signOut(this);
                return true;
            default:
                return onOptionsItemSelected(item);
        }
    }

}
